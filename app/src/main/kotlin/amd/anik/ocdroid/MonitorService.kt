package amd.anik.ocdroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Keeps one server-sent-events connection open while the app is backgrounded and turns
 * agent state transitions into native notifications. This is the only part of OCDroid
 * that does not live in the WebView.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var activeCall: Call? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var settings: SettingsStore

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val lastNotified = mutableMapOf<String, Long>()
    private val sessionTitles = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        ensureChannels(this)
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OCDroid::monitor"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_DISCONNECT) {
            // Disconnect stops monitoring and exits the app. It intentionally does not clear
            // the saved server settings, so the user stays signed in for the next launch.
            MonitorStatus.setState(this, "stopped", "disconnected")
            monitorJob?.cancel()
            activeCall?.cancel()
            scope.cancel()
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
            return START_NOT_STICKY
        }
        if (!settings.isConfigured ||
            !settings.backgroundMonitoring ||
            !settings.notificationsEnabled
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            buildMonitoringNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        MonitorStatus.setState(this, "starting", settings.serverUrl)
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        MonitorStatus.setState(this, "stopped", "background monitoring is off")
        monitorJob?.cancel()
        activeCall?.cancel()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            var backoff = 1_000L
            while (isActive) {
                val connected = runCatching { readEvents() }.getOrDefault(false)
                if (connected) {
                    backoff = 1_000L
                } else {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    /** Opens /api/event (v2), /global/event, or /event (v1) and blocks while the stream is alive. */
    private fun readEvents(): Boolean {
        val base = settings.serverUrl.trimEnd('/')
        for (path in STREAM_PATHS) {
            val builder = Request.Builder()
                .url(base + path)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
            if (settings.password.isNotEmpty()) {
                builder.header(
                    "Authorization",
                    Credentials.basic(settings.username, settings.password)
                )
            }
            val call = client.newCall(builder.build())
            activeCall = call
            val response = try {
                call.execute()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "SSE connect failed: ${e.message}")
                MonitorStatus.setState(this, "error", "$path: ${e.message ?: e.javaClass.simpleName}")
                continue
            }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "SSE $path -> HTTP ${resp.code}")
                    MonitorStatus.setState(this, "error", "$path returned HTTP ${resp.code}")
                    return@use
                }
                val contentType = resp.header("Content-Type").orEmpty()
                if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                    // A proxy or SPA fallback can return HTML with HTTP 200; reading it would
                    // look "connected" while no events ever arrive.
                    MonitorStatus.setState(this, "error", "$path is not an event stream ($contentType)")
                    return@use
                }
                MonitorStatus.setState(this, "connected", settings.serverUrl + path)
                val source = resp.body?.source() ?: return@use
                var eventName = ""
                val data = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) handleEvent(eventName, data.toString())
                            eventName = ""
                            data.clear()
                        }
                        line.startsWith(":") -> Unit
                        line.startsWith("id:") || line.startsWith("retry:") -> Unit
                        line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trimStart())
                        }
                        line.startsWith("{") || line.startsWith("[") -> {
                            // Some v2 builds emit bare JSON lines rather than full SSE frames.
                            handleEvent(eventName, line)
                        }
                    }
                }
                MonitorStatus.setState(this, "reconnecting", "$path closed")
                return true
            }
        }
        return false
    }

    private fun handleEvent(eventName: String, data: String) {
        val event = EventParser.parse(eventName, data)
        val sessionId = event.sessionId

        if (event.title != null && sessionId != null) {
            sessionTitles[sessionId] = event.title
        }

        val kind = NotificationRules.classify(event) ?: return
        MonitorStatus.recordEvent(this, event.type)

        // The user is reading this conversation, so an alert would be noise. A different
        // conversation still notifies even when the app is in the foreground.
        if (ActiveSessionStore.isViewing(this, sessionId)) return

        when (kind) {
            NotificationKind.ATTENTION -> notifyAttention(sessionId, event.title)
            NotificationKind.TASK_FINISHED ->
                notifyFinished(sessionId, event.title, failed = false, interrupted = false)
            NotificationKind.TASK_FAILED ->
                notifyFinished(sessionId, event.title, failed = true, interrupted = false)
            NotificationKind.TASK_INTERRUPTED ->
                notifyFinished(sessionId, event.title, failed = false, interrupted = true)
        }
    }

    private fun notifyFinished(
        sessionId: String?,
        parsedTitle: String?,
        failed: Boolean,
        interrupted: Boolean,
    ) {
        val key = sessionId ?: "global"
        if (!claimThrottle("done-$key", 15_000L)) return

        val fallbackTitle = sessionId?.let { sessionTitles[it] }
        val title = parsedTitle ?: fallbackTitle ?: when {
            failed -> getString(R.string.task_failed_title)
            interrupted -> getString(R.string.task_interrupted_title)
            else -> getString(R.string.task_finished_title)
        }
        val text = when {
            failed -> getString(R.string.task_failed_text)
            interrupted -> getString(R.string.task_interrupted_text)
            else -> getString(R.string.task_finished_text)
        }
        post(
            id = taskNotificationId(sessionId),
            channel = CHANNEL_TASKS,
            title = title,
            text = text,
            priority = NotificationCompat.PRIORITY_HIGH,
            vibration = TASK_VIBRATION,
        )
    }

    private fun notifyAttention(sessionId: String?, parsedTitle: String?) {
        val key = "attention-${sessionId ?: "global"}"
        if (!claimThrottle(key, 15_000L)) return

        val title = parsedTitle ?: getString(R.string.permission_title)
        val text = if (parsedTitle != null) {
            getString(R.string.attention_text)
        } else {
            getString(R.string.permission_text)
        }
        post(
            id = attentionNotificationId(sessionId),
            channel = CHANNEL_PERMISSIONS,
            title = title,
            text = text,
            priority = NotificationCompat.PRIORITY_HIGH,
            vibration = ATTENTION_VIBRATION,
        )
    }

    private fun claimThrottle(key: String, windowMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        if (now - (lastNotified[key] ?: 0L) < windowMillis) return false
        lastNotified[key] = now
        return true
    }

    private fun post(
        id: Int,
        channel: String,
        title: String,
        text: String,
        priority: Int,
        vibration: LongArray,
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            // The channel owns sound and vibration on Android 8+, but setting these too keeps
            // the notification correct on Android versions that ignore channel audio.
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(notificationSound())
            .setVibrate(vibration)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
        MonitorStatus.recordNotification(this, title)
    }

    private fun notificationSound(): Uri = Settings.System.DEFAULT_NOTIFICATION_URI

    private fun buildMonitoringNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val settingsPending = PendingIntent.getActivity(
            this,
            1,
            Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectPending = PendingIntent.getService(
            this,
            2,
            Intent(this, MonitorService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setColor(Color.parseColor("#7C9CFF"))
            .setContentTitle(getString(R.string.monitoring_title))
            .setContentText(getString(R.string.monitoring_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.monitoring_text)))
            .setContentIntent(pending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.disconnect),
                disconnectPending,
            )
            .addAction(
                android.R.drawable.ic_menu_manage,
                getString(R.string.settings_title),
                settingsPending,
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "MonitorService"
        private const val FOREGROUND_ID = 41
        const val TASKS_CHANNEL_ID = "tasks_v3"
        private const val CHANNEL_MONITORING = "monitoring_v2"
        private const val CHANNEL_TASKS = TASKS_CHANNEL_ID
        private const val CHANNEL_PERMISSIONS = "attention_v3"
        private const val ACTION_STOP = "amd.anik.ocdroid.STOP_MONITOR"
        private const val ACTION_DISCONNECT = "amd.anik.ocdroid.DISCONNECT_MONITOR"
        private val STREAM_PATHS = listOf("/api/event", "/global/event", "/event")
        private val TASK_VIBRATION = longArrayOf(0L, 220L, 140L, 220L)
        private val ATTENTION_VIBRATION = longArrayOf(0L, 420L, 180L, 420L)

        private fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private fun taskNotificationId(sessionId: String?): Int =
            (sessionId ?: "global").hashCode()

        private fun attentionNotificationId(sessionId: String?): Int =
            "attention-${sessionId ?: "global"}".hashCode()

        fun cancelSessionNotifications(context: Context, sessionId: String) {
            if (sessionId.isBlank()) return
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(taskNotificationId(sessionId))
            manager.cancel(attentionNotificationId(sessionId))
        }

        fun start(context: Context) {
            val store = SettingsStore(context)
            if (!store.isConfigured || !store.backgroundMonitoring || !store.notificationsEnabled) {
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MonitorService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }

        /**
         * Used by Settings to prove the channel, permission and alarm settings end to end.
         * This does not depend on the server being reachable.
         */
        fun showTestNotification(context: Context) {
            ensureChannels(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.test_notification_title))
                .setContentText(context.getString(R.string.test_notification_text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(TASK_VIBRATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(TEST_NOTIFICATION_ID, notification)
            MonitorStatus.recordNotification(context, context.getString(R.string.test_notification_title))
        }

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MONITORING,
                    context.getString(R.string.monitoring_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.monitoring_channel_desc)
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_TASKS,
                    context.getString(R.string.tasks_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.tasks_channel_desc)
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes())
                    enableVibration(true)
                    vibrationPattern = TASK_VIBRATION
                    enableLights(true)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PERMISSIONS,
                    context.getString(R.string.permission_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.permission_text)
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes())
                    enableVibration(true)
                    vibrationPattern = ATTENTION_VIBRATION
                    enableLights(true)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }

        private const val TEST_NOTIFICATION_ID = 42
    }
}

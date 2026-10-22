package amd.anik.ocdroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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

    private val busySessions = mutableSetOf<String>()
    private val lastNotified = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        createChannels()
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
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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

    /** Opens /api/event (v2) or /event (v1) and blocks while the stream is alive. */
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
                continue
            }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "SSE $path -> HTTP ${resp.code}")
                    return@use
                }
                val source = resp.body?.source() ?: return@use
                var eventName = ""
                val data = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) {
                                handleEvent(eventName, data.toString())
                                eventName = ""
                                data.clear()
                            }
                        }
                        line.startsWith(":") -> Unit
                        line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trimStart())
                        }
                        else -> {
                            // Some v2 builds emit bare JSON lines rather than full SSE frames.
                            handleEvent(eventName, line)
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    private fun handleEvent(eventName: String, data: String) {
        val event = EventParser.parse(eventName, data)
        val type = event.type
        val sessionId = event.sessionId

        if (type.contains("permission.asked") || (type.contains("permission") && !type.contains("replied"))) {
            notifyPermission(sessionId)
            return
        }

        if (type == "session.execution.started" || type.contains("session.busy")) {
            sessionId?.let { busySessions += it }
            return
        }

        if (type == "session.execution.succeeded" ||
            type == "session.execution.failed" ||
            type == "session.execution.interrupted"
        ) {
            if (event.shutdown) return
            sessionId?.let { busySessions.remove(it) }
            val failed = type == "session.execution.failed"
            val interrupted = type == "session.execution.interrupted"
            notifyFinished(sessionId, event.title, failed, interrupted)
            return
        }

        if (event.status == "busy") {
            sessionId?.let { busySessions += it }
            return
        }

        // OpenCode v1 fallback.
        val idle = type.contains("session.idle") || event.status == "idle"
        if (!idle) return

        val finished = when {
            sessionId != null && busySessions.remove(sessionId) -> true
            sessionId == null && busySessions.isNotEmpty() -> {
                busySessions.clear()
                true
            }
            else -> false
        }
        if (finished) notifyFinished(sessionId, event.title, failed = false, interrupted = false)
    }

    private fun notifyFinished(
        sessionId: String?,
        parsedTitle: String?,
        failed: Boolean,
        interrupted: Boolean,
    ) {
        val key = sessionId ?: "global"
        val now = System.currentTimeMillis()
        if (now - (lastNotified[key] ?: 0L) < 15_000L) return
        lastNotified[key] = now

        val title = parsedTitle ?: when {
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
            id = key.hashCode(),
            channel = CHANNEL_TASKS,
            title = title,
            text = text,
            priority = NotificationCompat.PRIORITY_DEFAULT,
        )
    }

    private fun notifyPermission(sessionId: String?) {
        val key = "perm-${sessionId ?: "global"}"
        val now = System.currentTimeMillis()
        if (now - (lastNotified[key] ?: 0L) < 30_000L) return
        lastNotified[key] = now
        post(
            id = key.hashCode(),
            channel = CHANNEL_PERMISSIONS,
            title = getString(R.string.permission_title),
            text = getString(R.string.permission_text),
            priority = NotificationCompat.PRIORITY_HIGH,
        )
    }

    private fun post(id: Int, channel: String, title: String, text: String, priority: Int) {
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
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

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
        val host = runCatching { android.net.Uri.parse(settings.serverUrl).host }.getOrNull()
            ?: settings.serverUrl
        return NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.monitoring_title))
            .setContentText(getString(R.string.monitoring_text, host))
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITORING,
                getString(R.string.monitoring_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.monitoring_channel_desc)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TASKS,
                getString(R.string.tasks_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.tasks_channel_desc)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PERMISSIONS,
                getString(R.string.permission_title),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.permission_text)
            }
        )
    }

    companion object {
        private const val TAG = "MonitorService"
        private const val FOREGROUND_ID = 41
        private const val CHANNEL_MONITORING = "monitoring"
        private const val CHANNEL_TASKS = "tasks"
        private const val CHANNEL_PERMISSIONS = "permissions"
        private const val ACTION_STOP = "amd.anik.ocdroid.STOP_MONITOR"
        private val STREAM_PATHS = listOf("/api/event", "/event")

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
    }
}

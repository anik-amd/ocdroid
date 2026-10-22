package amd.anik.ocdroid

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendTestNotification() else openAppNotificationSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        setContentView(R.layout.activity_settings)

        val urlInput = findViewById<TextInputEditText>(R.id.urlInput)
        val usernameInput = findViewById<TextInputEditText>(R.id.usernameInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val notificationsSwitch = findViewById<MaterialSwitch>(R.id.notificationsSwitch)
        val monitoringSwitch = findViewById<MaterialSwitch>(R.id.monitoringSwitch)
        val bundledUiSwitch = findViewById<MaterialSwitch>(R.id.bundledUiSwitch)
        val monitorStatus = findViewById<TextView>(R.id.monitorStatus)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        urlInput.setText(settings.serverUrl)
        usernameInput.setText(settings.username)
        passwordInput.setText(settings.password)
        notificationsSwitch.isChecked = settings.notificationsEnabled
        monitoringSwitch.isChecked = settings.backgroundMonitoring
        bundledUiSwitch.isChecked = settings.useBundledUi
        refreshMonitorStatus(monitorStatus)

        findViewById<MaterialButton>(R.id.testNotificationButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@setOnClickListener
            }
            sendTestNotification()
        }

        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener {
            val normalized = normalizeServerUrl(urlInput.text?.toString().orEmpty())
            if (normalized == null) {
                Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            settings.save(
                normalized,
                usernameInput.text?.toString().orEmpty(),
                passwordInput.text?.toString().orEmpty(),
            )
            settings.notificationsEnabled = notificationsSwitch.isChecked
            settings.backgroundMonitoring = monitoringSwitch.isChecked
            settings.useBundledUi = bundledUiSwitch.isChecked
            if (settings.notificationsEnabled && settings.backgroundMonitoring) {
                MonitorService.start(this)
            } else {
                MonitorService.stop(this)
            }
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<MaterialButton>(R.id.clearCacheButton).setOnClickListener {
            WebView(this).apply {
                clearCache(true)
                clearFormData()
                clearHistory()
                destroy()
            }
            Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendTestNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(MonitorService.TASKS_CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                openChannelSettings()
                return
            }
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            openAppNotificationSettings()
            return
        }
        MonitorService.showTestNotification(this)
        Toast.makeText(this, R.string.test_notification_sent, Toast.LENGTH_SHORT).show()
        refreshMonitorStatus(findViewById(R.id.monitorStatus))
    }

    private fun refreshMonitorStatus(view: TextView) {
        val snapshot = MonitorStatus.snapshot(this)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getNotificationChannel(MonitorService.TASKS_CHANNEL_ID)
        } else {
            null
        }
        val notifications = when {
            !NotificationManagerCompat.from(this).areNotificationsEnabled() -> "blocked in Android settings"
            channel?.importance == NotificationManager.IMPORTANCE_NONE -> "task channel turned off"
            else -> "allowed"
        }
        view.text = buildString {
            append("Connection: ")
            append(snapshot.state)
            if (snapshot.detail.isNotBlank()) append(" — ").append(snapshot.detail)
            append("\nLast event: ")
            append(snapshot.lastEvent)
            if (snapshot.lastEventAt > 0) append(" (").append(ago(snapshot.lastEventAt)).append(")")
            append("\nNotifications: ")
            append(snapshot.notificationCount).append(" sent, ").append(notifications)
            append("\nLast notification: ")
            append(snapshot.lastNotification)
            if (snapshot.lastNotificationAt > 0) append(" (").append(ago(snapshot.lastNotificationAt)).append(")")
        }
    }

    private fun ago(timestamp: Long): String {
        val seconds = ((System.currentTimeMillis() - timestamp) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(intent) }
    }

    private fun openChannelSettings() {
        val intent = Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, MonitorService.TASKS_CHANNEL_ID)
        runCatching { startActivity(intent) }
    }
}

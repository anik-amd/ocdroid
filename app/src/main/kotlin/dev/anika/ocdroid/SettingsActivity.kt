package dev.anika.ocdroid

import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore

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

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        urlInput.setText(settings.serverUrl)
        usernameInput.setText(settings.username)
        passwordInput.setText(settings.password)
        notificationsSwitch.isChecked = settings.notificationsEnabled
        monitoringSwitch.isChecked = settings.backgroundMonitoring
        bundledUiSwitch.isChecked = settings.useBundledUi

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
}

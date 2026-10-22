package amd.anik.ocdroid

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class ServerConfig(
    val url: String,
    val username: String,
    val password: String,
)

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ocdroid_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("ocdroid_fallback", Context.MODE_PRIVATE)
    }

    var serverUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SERVER_USERNAME
        set(value) = prefs.edit().putString(KEY_USERNAME, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var setupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var backgroundMonitoring: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING, value).apply()

    var useBundledUi: Boolean
        get() = prefs.getBoolean(KEY_BUNDLED_UI, true)
        set(value) = prefs.edit().putBoolean(KEY_BUNDLED_UI, value).apply()

    val isConfigured: Boolean
        get() = setupCompleted && serverUrl.isNotBlank()

    val config: ServerConfig
        get() = ServerConfig(serverUrl, username, password)

    fun save(url: String, user: String, pass: String) {
        serverUrl = url
        username = user
        password = pass
        setupCompleted = true
    }

    fun clearPassword() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_MONITORING = "monitoring"
        private const val KEY_BUNDLED_UI = "bundled_ui"
    }
}

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
private val PRIVATE_HOST = Regex(
    "^(localhost|127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|.*\\.local)(:\\d+)?$",
    RegexOption.IGNORE_CASE,
)

/**
 * Turns user input into a canonical origin (scheme, host and port only).
 *
 * Local addresses default to http:// because OpenCode's own web server is plain HTTP;
 * public hostnames default to https://. Paths are intentionally dropped: the WebView
 * always starts at the server root, while pairing links are parsed separately.
 */
fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    val candidate = if (SCHEME.containsMatchIn(trimmed)) {
        trimmed
    } else {
        val hostPart = trimmed.substringBefore('/')
        val scheme = if (PRIVATE_HOST.matches(hostPart) || !hostPart.contains('.')) "http" else "https"
        "$scheme://$trimmed"
    }

    return runCatching {
        val uri = java.net.URI(candidate)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port != -1) ":${uri.port}" else ""
        "$scheme://$host$port"
    }.getOrNull()
}

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

    private val appContext = context.applicationContext

    /**
     * Non-secret settings may live here if Android Keystore is unavailable. The password
     * is never written to this file: it stays in encrypted storage or in memory only.
     */
    private val fallbackPrefs: SharedPreferences =
        appContext.getSharedPreferences("ocdroid_fallback", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "ocdroid_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    private val prefs: SharedPreferences
        get() = securePrefs ?: fallbackPrefs

    @Volatile
    private var memoryPassword: String = ""

    init {
        val legacyPassword = fallbackPrefs.getString(KEY_PASSWORD, null)
        if (securePrefs != null) {
            // Migrate a password left by older builds, then remove the plaintext copy.
            if (!legacyPassword.isNullOrEmpty() &&
                securePrefs.getString(KEY_PASSWORD, null).isNullOrEmpty()
            ) {
                securePrefs.edit().putString(KEY_PASSWORD, legacyPassword).apply()
            }
            fallbackPrefs.edit().remove(KEY_PASSWORD).apply()
        } else if (!legacyPassword.isNullOrEmpty()) {
            // Keystore failed: keep the session usable until process death, but never persist
            // the token in plaintext.
            memoryPassword = legacyPassword
            fallbackPrefs.edit().remove(KEY_PASSWORD).apply()
        }
    }

    val encryptedStorage: Boolean
        get() = securePrefs != null

    var serverUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SERVER_USERNAME
        set(value) = prefs.edit().putString(KEY_USERNAME, value.trim()).apply()

    var password: String
        get() = securePrefs?.getString(KEY_PASSWORD, null) ?: memoryPassword
        set(value) {
            val secure = securePrefs
            if (secure != null) {
                secure.edit().putString(KEY_PASSWORD, value).apply()
            } else {
                memoryPassword = value
            }
        }

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
        securePrefs?.edit()?.remove(KEY_PASSWORD)?.apply()
        memoryPassword = ""
        fallbackPrefs.edit().remove(KEY_PASSWORD).apply()
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
    "^(localhost|127\\.\\d+\\.\\d+\\.\\d+|10\\.\\d+\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+" +
        "|172\\.(1[6-9]|2[0-9]|3[01])\\.\\d+\\.\\d+|\\[?::1\\]?|.*\\.local)(:\\d+)?$",
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

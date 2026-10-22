package amd.anik.ocdroid

import java.net.URI

/**
 * Compares web origins without pulling Android classes into JVM tests.
 *
 * OCDroid only grants the WebView bridge, camera permission, HTTP auth and bundled assets to
 * the exact origin the user configured. Comparing hosts alone would also match a different
 * port or an `http://` downgrade of the same host.
 */
object ServerOrigin {

    fun normalize(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = hostOf(uri) ?: return null
            val port = when {
                uri.port != -1 -> uri.port
                scheme == "https" -> 443
                else -> 80
            }
            val displayHost = if (host.contains(':')) "[$host]" else host
            "$scheme://$displayHost:$port"
        }.getOrNull()
    }

    fun same(first: String?, second: String?): Boolean {
        val a = normalize(first) ?: return false
        val b = normalize(second) ?: return false
        return a == b
    }

    fun host(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { hostOf(URI(url.trim())) }.getOrNull()
    }

    private fun hostOf(uri: URI): String? = uri.host
        ?.lowercase()
        ?.trimEnd('.')
        ?.removeSurrounding("[", "]")
        ?.takeIf { it.isNotBlank() }
}

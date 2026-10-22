package amd.anik.ocdroid

/**
 * The official web client routes a conversation as
 * `/server/<encoded server key>/session/<session id>`.
 *
 * Parsing it in Kotlin as well as in the injected script gives the activity a second chance to
 * learn the open conversation when a WebView does not deliver a JavaScript callback.
 */
object SessionRoutes {

    private val sessionPath = Regex("^/server/[^/]+/session/([^/?#]+)/?$")

    fun sessionId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val path = runCatching { java.net.URI(url).path }.getOrNull() ?: return null
        val encoded = sessionPath.find(path)?.groupValues?.get(1) ?: return null
        return runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
            .takeIf { it.isNotBlank() }
    }
}

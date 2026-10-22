package dev.anika.ocdroid

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Serves the mirrored official web client from the APK.
 *
 * Only files that actually exist in assets/web are intercepted. Everything else
 * (notably /api/* and SSE streams) continues to the real server untouched.
 */
class WebBundle(private val context: Context, private val settings: SettingsStore) {

    private val indexHtml: String? by lazy {
        runCatching {
            context.assets.open("web/index.html").bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    val available: Boolean
        get() = indexHtml != null

    private val bundledMainScript: String? by lazy {
        indexHtml?.let { extractMainScript(it) }
    }

    private val liveCheckClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    fun response(uri: Uri, isMainFrame: Boolean): WebResourceResponse? {
        if (!settings.useBundledUi || indexHtml == null) return null
        val path = uri.path.orEmpty()
        if (path.startsWith("/api/")) return null

        val relative = path.removePrefix("/")
        val candidate = when {
            relative.isEmpty() -> "index.html"
            pathExists(relative) -> relative
            isMainFrame && !relative.substringAfterLast('/').contains('.') -> "index.html"
            else -> null
        } ?: return null

        val stream = runCatching { context.assets.open("web/$candidate") }.getOrNull() ?: return null
        return streamResponse(candidate, stream)
    }

    private fun pathExists(relative: String): Boolean = runCatching {
        context.assets.open("web/$relative").close()
        true
    }.getOrDefault(false)

    private fun streamResponse(path: String, stream: InputStream): WebResourceResponse {
        val mime = mimeType(path)
        val encoding = if (mime.startsWith("text/") || mime.contains("json") || mime.contains("javascript") || mime.contains("xml")) "utf-8" else null
        return WebResourceResponse(mime, encoding, stream).apply {
            val headers = mutableMapOf(
                "Cache-Control" to if (path == "index.html") "no-cache" else "public, max-age=31536000, immutable",
                "X-Content-Type-Options" to "nosniff",
            )
            responseHeaders = headers
            statusCode = 200
            reasonPhrase = "OK"
        }
    }

    /**
     * Fetches the live index once per launch. If the server's web client differs from
     * this APK's mirror, network loading is used from the next launch so the UI never
     * silently runs an old client against a newer API.
     */
    suspend fun checkForUpdates() = withContext(Dispatchers.IO) {
        val bundled = bundledMainScript ?: return@withContext
        val request = Request.Builder()
            .url(settings.serverUrl.trimEnd('/') + "/")
            .header("Cache-Control", "no-cache")
            .apply {
                if (settings.password.isNotEmpty()) {
                    header("Authorization", Credentials.basic(settings.username, settings.password))
                }
            }
            .build()
        runCatching {
            liveCheckClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val html = response.body?.string() ?: return@withContext
                val live = extractMainScript(html) ?: return@withContext
                settings.useBundledUi = live == bundled
            }
        }
    }

    private fun extractMainScript(html: String): String? =
        Regex("src=\"(/[^\"]*index-[^\"]+\\.js)\"").find(html)?.groupValues?.get(1)

    companion object {
        private fun mimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json", "webmanifest" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "wasm" -> "application/wasm"
            "map" -> "application/json"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}

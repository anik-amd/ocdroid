package amd.anik.ocdroid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Parses and redeems the pairing links and QR codes printed by `opencode pair`.
 *
 * OpenCode accepts `GET /auth/connect/<code>` with `Accept: application/json` and returns a
 * session token. That token is accepted anywhere the server password is, so OCDroid can
 * store it as the password and keep using ordinary Basic auth in the WebView.
 */
data class PairingPayload(
    val urls: List<String>,
    val code: String,
)

sealed interface PairingResult {
    data class Paired(val url: String, val token: String) : PairingResult
    data object Expired : PairingResult
    data object Unreachable : PairingResult
    data object Unsupported : PairingResult
}

object Pairing {

    private val codePattern = Regex("[A-Za-z0-9_-]+")

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun parse(text: String): PairingPayload? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return parseQrPayload(trimmed)
        return parseLink(trimmed)
    }

    private fun parseQrPayload(text: String): PairingPayload? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val code = json.optString("code")
        if (!codePattern.matches(code)) return null
        val array = json.optJSONArray("urls") ?: return null
        val urls = buildList {
            for (index in 0 until array.length()) {
                normalizeServerUrl(array.optString(index))?.let(::add)
            }
        }.distinct()
        if (urls.isEmpty()) return null
        return PairingPayload(urls, code)
    }

    private fun parseLink(text: String): PairingPayload? {
        val uri = runCatching { java.net.URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (segments.size != 3 || segments[0] != "auth" || segments[1] != "connect") return null
        val code = segments[2]
        if (!codePattern.matches(code)) return null
        val authority = uri.rawAuthority ?: return null
        val origin = normalizeServerUrl("$scheme://$authority") ?: return null
        return PairingPayload(listOf(origin), code)
    }

    suspend fun redeem(payload: PairingPayload): PairingResult =
        withContext(Dispatchers.IO) {
            var unreachable = false
            var unsupported = false
            for (url in payload.urls) {
                when (val result = redeemOne(url, payload.code)) {
                    // The server just issued this token; a second health check only adds a way
                    // to throw a valid token away (for example when /api/info is proxied oddly).
                    is PairingResult.Paired -> return@withContext result
                    PairingResult.Expired -> return@withContext PairingResult.Expired
                    PairingResult.Unsupported -> unsupported = true
                    PairingResult.Unreachable -> unreachable = true
                }
            }
            when {
                unsupported -> PairingResult.Unsupported
                unreachable -> PairingResult.Unreachable
                else -> PairingResult.Unreachable
            }
        }

    /**
     * The address to load the official web login page from. A QR code can contain the local
     * address first (useful on the computer) and a tunnel later; the phone needs the latter.
     * Prefer HTTPS when one is offered because the official QR scanner needs a secure origin.
     * Returns null when every address points back at the phone itself.
     */
    fun preferredServer(payload: PairingPayload): String? {
        val usable = payload.urls.filterNot { isLoopback(it) }
        return usable.firstOrNull { it.startsWith("https://", ignoreCase = true) }
            ?: usable.firstOrNull()
    }

    /** True when this address only works on the computer that printed it. */
    fun isLoopback(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull()
            ?.lowercase()
            ?.removeSurrounding("[", "]")
            ?: return false
        return host == "localhost" ||
            host == "::1" ||
            host.endsWith(".localhost") ||
            host.startsWith("127.")
    }

    private fun redeemOne(base: String, code: String): PairingResult {
        val request = Request.Builder()
            .url("$base/auth/connect/$code")
            .header("Accept", "application/json")
            .build()
        val response = runCatching { client.newCall(request).execute() }.getOrNull()
            ?: return PairingResult.Unreachable
        response.use {
            return when {
                it.code in 200..299 -> {
                    val token = runCatching {
                        JSONObject(it.peekBody(64 * 1024).string()).optString("token")
                    }.getOrNull().orEmpty()
                    if (token.isBlank()) PairingResult.Unsupported
                    else PairingResult.Paired(base, token)
                }
                it.code == 401 || it.code == 410 -> PairingResult.Expired
                it.code == 404 -> PairingResult.Unsupported
                else -> PairingResult.Unreachable
            }
        }
    }

}

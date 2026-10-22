package amd.anik.ocdroid

/**
 * Maps a request path to a file inside the packaged `assets/web` directory.
 *
 * The WebView hands us URLs, so this deliberately rejects dot segments, empty segments,
 * backslashes and NUL bytes instead of relying on the asset manager to normalise them.
 */
object AssetPaths {

    fun underWeb(relative: String?): String? {
        val clean = relative?.trim()?.trimStart('/') ?: return null
        if (clean.isEmpty()) return null
        if (clean.contains('\\') || clean.contains('\u0000')) return null
        if (clean.contains("%2e", ignoreCase = true) ||
            clean.contains("%2f", ignoreCase = true) ||
            clean.contains("%5c", ignoreCase = true)
        ) {
            return null
        }
        val parts = clean.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return "web/$clean"
    }
}

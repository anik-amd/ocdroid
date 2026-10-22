package amd.anik.ocdroid

/**
 * Parses OpenCode v1/v2 SSE payloads without Android dependencies so the rules can be
 * covered by fast JVM unit tests.
 */
data class ParsedEvent(
    val type: String,
    val sessionId: String?,
    val status: String?,
    val title: String?,
    val shutdown: Boolean,
)

object EventParser {

    private val typeRegex = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"")
    private val sessionIdRegex =
        Regex("\"(?:sessionID|sessionId|session_id)\"\\s*:\\s*\"([^\"]+)\"")
    private val statusRegex =
        Regex("\"status\"\\s*:\\s*\\{\\s*\"type\"\\s*:\\s*\"(busy|idle|retry)\"")
    private val titleRegex = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"")
    private val shutdownRegex = Regex("\"reason\"\\s*:\\s*\"shutdown\"")

    fun parse(eventName: String, data: String): ParsedEvent {
        val name = eventName.trim()
        // v1 SSE frames use a generic "event: message" line and put the real type in
        // the JSON payload, so only a specific event name may override the payload.
        val type = if (name.isNotBlank() && name.lowercase() != "message") {
            name
        } else {
            typeRegex.find(data)?.groupValues?.get(1).orEmpty()
        }.lowercase()

        return ParsedEvent(
            type = type,
            sessionId = sessionIdRegex.find(data)?.groupValues?.get(1),
            status = statusRegex.find(data)?.groupValues?.get(1)?.lowercase(),
            title = titleRegex.find(data)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() && it.length <= 80 },
            shutdown = shutdownRegex.containsMatchIn(data),
        )
    }
}

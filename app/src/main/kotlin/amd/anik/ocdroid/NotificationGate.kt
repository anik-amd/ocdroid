package amd.anik.ocdroid

/**
 * Decides whether an event belongs to the conversation that is on screen right now.
 *
 * Keeping this rule free of Android types makes the "don't notify about the conversation I am
 * already reading" behaviour testable without an emulator.
 */
object NotificationGate {

    fun shouldSuppress(
        sessionId: String?,
        activeSessionId: String?,
        appInForeground: Boolean,
    ): Boolean {
        if (!appInForeground) return false
        val session = sessionId?.takeIf { it.isNotBlank() } ?: return false
        return session == activeSessionId
    }
}

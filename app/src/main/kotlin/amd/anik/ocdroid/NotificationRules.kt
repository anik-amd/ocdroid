package amd.anik.ocdroid

/** What the monitor should tell the user about one OpenCode event. */
enum class NotificationKind {
    ATTENTION,
    TASK_FINISHED,
    TASK_FAILED,
    TASK_INTERRUPTED,
}

/**
 * Notification decisions are kept free of Android types so every event shape can be
 * covered by JVM tests instead of trial-and-error on a phone.
 */
object NotificationRules {

    fun classify(event: ParsedEvent): NotificationKind? {
        val type = event.type

        if (type == "server.connected" || type == "server.heartbeat") return null

        if (type.contains("permission.asked") ||
            (type.contains("permission") && !type.contains("replied"))
        ) {
            return NotificationKind.ATTENTION
        }

        if (type.contains("form.created") ||
            type.contains("question.asked") ||
            type.contains("question.pending")
        ) {
            return NotificationKind.ATTENTION
        }

        when (type) {
            "session.execution.succeeded" -> return NotificationKind.TASK_FINISHED
            "session.execution.failed", "session.error" -> return NotificationKind.TASK_FAILED
            "session.execution.interrupted" ->
                return if (event.shutdown) null else NotificationKind.TASK_INTERRUPTED
        }

        // v2 sends session.status {status:{type:"idle"}}; session.idle remains as a
        // deprecated compatibility event, and v1 uses the same idle status shape.
        if (type.contains("session.idle") || event.status == "idle") {
            return NotificationKind.TASK_FINISHED
        }

        return null
    }
}

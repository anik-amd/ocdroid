package amd.anik.ocdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationRulesTest {

    @Test
    fun completionEventsNotify() {
        assertEquals(
            NotificationKind.TASK_FINISHED,
            NotificationRules.classify(EventParser.parse("", """{"type":"session.execution.succeeded","data":{"sessionID":"s"}}""")),
        )
        assertEquals(
            NotificationKind.TASK_FAILED,
            NotificationRules.classify(EventParser.parse("", """{"type":"session.execution.failed","data":{"sessionID":"s"}}""")),
        )
        assertEquals(
            NotificationKind.TASK_INTERRUPTED,
            NotificationRules.classify(EventParser.parse("", """{"type":"session.execution.interrupted","data":{"sessionID":"s"}}""")),
        )
    }

    @Test
    fun idleStatusNotifiesEvenIfBusyWasMissed() {
        val event = EventParser.parse(
            "",
            """{"type":"session.status","data":{"sessionID":"s","status":{"type":"idle"}}}""",
        )
        assertEquals(NotificationKind.TASK_FINISHED, NotificationRules.classify(event))
        assertEquals(
            NotificationKind.TASK_FINISHED,
            NotificationRules.classify(EventParser.parse("", """{"type":"session.idle","data":{"sessionID":"s"}}""")),
        )
    }

    @Test
    fun attentionEventsNotify() {
        assertEquals(
            NotificationKind.ATTENTION,
            NotificationRules.classify(EventParser.parse("", """{"type":"permission.asked","data":{"sessionID":"s"}}""")),
        )
        assertEquals(
            NotificationKind.ATTENTION,
            NotificationRules.classify(EventParser.parse("", """{"type":"form.created","data":{"sessionID":"s"}}""")),
        )
    }

    @Test
    fun repliedPermissionsAndNoiseDoNotNotify() {
        assertNull(
            NotificationRules.classify(
                EventParser.parse("", """{"type":"permission.replied","data":{"sessionID":"s"}}"""),
            ),
        )
        assertNull(
            NotificationRules.classify(
                EventParser.parse("", """{"type":"server.connected","data":{}}"""),
            ),
        )
        assertNull(
            NotificationRules.classify(
                EventParser.parse("", """{"type":"session.status","data":{"sessionID":"s","status":{"type":"busy"}}}"""),
            ),
        )
        assertNull(
            NotificationRules.classify(
                EventParser.parse(
                    "",
                    """{"type":"session.execution.interrupted","data":{"sessionID":"s","reason":"shutdown"}}""",
                ),
            ),
        )
    }
}

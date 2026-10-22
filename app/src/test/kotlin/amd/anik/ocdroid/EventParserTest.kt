package amd.anik.ocdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventParserTest {

    @Test
    fun parsesV2ExecutionSuccess() {
        val event = EventParser.parse(
            "",
            """{"type":"session.execution.succeeded","data":{"sessionID":"ses_123"}}""",
        )
        assertEquals("session.execution.succeeded", event.type)
        assertEquals("ses_123", event.sessionId)
        assertFalse(event.shutdown)
    }

    @Test
    fun eventNameWinsOverPayloadType() {
        val event = EventParser.parse(
            "permission.asked",
            """{"type":"message.updated","properties":{"sessionID":"ses_9"}}""",
        )
        assertEquals("permission.asked", event.type)
        assertEquals("ses_9", event.sessionId)
    }

    @Test
    fun genericSseEventNameUsesPayloadType() {
        val event = EventParser.parse(
            "message",
            """{"type":"session.status","data":{"sessionID":"ses_9","status":{"type":"idle"}}}""",
        )
        assertEquals("session.status", event.type)
        assertEquals("idle", event.status)
    }

    @Test
    fun parsesBusyStatusForV1Fallback() {
        val event = EventParser.parse(
            "",
            """{"type":"session.updated","properties":{"status":{"type":"busy"}}}""",
        )
        assertEquals("busy", event.status)
    }

    @Test
    fun detectsShutdownInterrupts() {
        val event = EventParser.parse(
            "",
            """{"type":"session.execution.interrupted","data":{"sessionID":"s","reason":"shutdown"}}""",
        )
        assertTrue(event.shutdown)
    }

    @Test
    fun acceptsShutdownWithWhitespace() {
        val event = EventParser.parse(
            "",
            """{"type":"session.execution.interrupted","data":{"reason" : "shutdown"}}""",
        )
        assertTrue(event.shutdown)
    }

    @Test
    fun readsAndCapsTitles() {
        val short = EventParser.parse("", """{"title":"Fix login"}""")
        assertEquals("Fix login", short.title)

        val long = EventParser.parse("", """{"title":"${"x".repeat(120)}"}""")
        assertNull(long.title)
    }

    @Test
    fun legacySessionIdKeysWork() {
        assertEquals("a", EventParser.parse("", """{"sessionId":"a"}""").sessionId)
        assertEquals("b", EventParser.parse("", """{"session_id":"b"}""").sessionId)
    }
}

package amd.anik.ocdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRoutesTest {

    @Test
    fun readsTheOpenConversationFromTheOfficialRoute() {
        assertEquals(
            "ses_abc123",
            SessionRoutes.sessionId("https://tunnel.example/server/dHVubmVs/session/ses_abc123"),
        )
        assertEquals(
            "ses_abc123",
            SessionRoutes.sessionId("http://192.168.1.4:4096/server/d2Vi/session/ses_abc123/?tab=1"),
        )
    }

    @Test
    fun homeAndServerRoutesHaveNoOpenConversation() {
        assertNull(SessionRoutes.sessionId("https://tunnel.example/"))
        assertNull(SessionRoutes.sessionId("https://tunnel.example/server/d2Vi"))
        assertNull(SessionRoutes.sessionId(null))
        assertNull(SessionRoutes.sessionId("not a url"))
    }
}

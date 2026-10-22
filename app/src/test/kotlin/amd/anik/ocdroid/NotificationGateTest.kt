package amd.anik.ocdroid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGateTest {

    @Test
    fun suppressesOnlyTheForegroundConversation() {
        assertTrue(NotificationGate.shouldSuppress("ses_open", "ses_open", appInForeground = true))
        assertFalse(NotificationGate.shouldSuppress("ses_other", "ses_open", appInForeground = true))
        assertFalse(NotificationGate.shouldSuppress("ses_open", "ses_open", appInForeground = false))
    }

    @Test
    fun globalEventsAndHomeScreenStillNotify() {
        assertFalse(NotificationGate.shouldSuppress(null, "ses_open", appInForeground = true))
        assertFalse(NotificationGate.shouldSuppress("", "ses_open", appInForeground = true))
        assertFalse(NotificationGate.shouldSuppress("ses_open", null, appInForeground = true))
    }
}

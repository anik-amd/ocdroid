package amd.anik.ocdroid

import android.content.Context
import android.os.PowerManager

/**
 * The conversation currently visible in the WebView, shared by the activity and the
 * background monitor. Both live in the same process, so this does not need a database.
 */
object ActiveSessionStore {

    @Volatile
    var activeSessionId: String? = null

    @Volatile
    var appInForeground: Boolean = false

    /**
     * True only while the user can actually see that conversation. A backgrounded app keeps
     * the same route, but then the notification is still useful.
     */
    fun isViewing(context: Context, sessionId: String?): Boolean {
        if (!NotificationGate.shouldSuppress(sessionId, activeSessionId, appInForeground)) return false
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isInteractive
    }
}

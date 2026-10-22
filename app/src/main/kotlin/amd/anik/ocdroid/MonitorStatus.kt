package amd.anik.ocdroid

import android.content.Context

/**
 * Small, non-secret status log shown in Settings. When notifications do not arrive this
 * distinguishes "the stream never connected" from "Android blocked the notification".
 */
data class MonitorSnapshot(
    val state: String,
    val detail: String,
    val lastEvent: String,
    val lastEventAt: Long,
    val notificationCount: Int,
    val lastNotification: String,
    val lastNotificationAt: Long,
)

object MonitorStatus {

    private const val PREFS = "monitor_status"
    private const val KEY_STATE = "state"
    private const val KEY_DETAIL = "detail"
    private const val KEY_EVENT = "event"
    private const val KEY_EVENT_AT = "event_at"
    private const val KEY_COUNT = "notification_count"
    private const val KEY_NOTIFICATION = "notification"
    private const val KEY_NOTIFICATION_AT = "notification_at"

    fun setState(context: Context, state: String, detail: String = "") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, state)
            .putString(KEY_DETAIL, detail)
            .apply()
    }

    fun recordEvent(context: Context, type: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_EVENT, type)
            .putLong(KEY_EVENT_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordNotification(context: Context, title: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_COUNT, prefs.getInt(KEY_COUNT, 0) + 1)
            .putString(KEY_NOTIFICATION, title)
            .putLong(KEY_NOTIFICATION_AT, System.currentTimeMillis())
            .apply()
    }

    fun snapshot(context: Context): MonitorSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return MonitorSnapshot(
            state = prefs.getString(KEY_STATE, "not started") ?: "not started",
            detail = prefs.getString(KEY_DETAIL, "") ?: "",
            lastEvent = prefs.getString(KEY_EVENT, "none") ?: "none",
            lastEventAt = prefs.getLong(KEY_EVENT_AT, 0L),
            notificationCount = prefs.getInt(KEY_COUNT, 0),
            lastNotification = prefs.getString(KEY_NOTIFICATION, "none") ?: "none",
            lastNotificationAt = prefs.getLong(KEY_NOTIFICATION_AT, 0L),
        )
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

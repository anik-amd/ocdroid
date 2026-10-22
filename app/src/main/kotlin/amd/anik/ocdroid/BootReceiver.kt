package amd.anik.ocdroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = SettingsStore(context)
        if (!store.isConfigured || !store.backgroundMonitoring || !store.notificationsEnabled) {
            return
        }
        runCatching { MonitorService.start(context) }
            .onFailure { Log.w("BootReceiver", "Could not restart monitoring", it) }
    }
}

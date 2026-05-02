package io.nikdmitryuk.ultraclient.android.vpn

import android.content.Context
import android.content.Intent

class KillSwitchManager(
    private val context: Context,
) {
    private var active = false

    fun activate() {
        active = true
        val intent = Intent(context, UltraVpnService::class.java)
        intent.action = UltraVpnService.ACTION_KILL_SWITCH
        context.startService(intent)
    }

    fun deactivate() {
        active = false
    }

    fun isActive(): Boolean = active
}

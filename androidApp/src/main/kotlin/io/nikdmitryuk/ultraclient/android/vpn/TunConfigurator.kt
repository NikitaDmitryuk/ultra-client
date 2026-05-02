package io.nikdmitryuk.ultraclient.android.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.SplitTunnelRule

class TunConfigurator(private val service: VpnService) {

    fun establish(antiDetectConfig: AntiDetectConfig): ParcelFileDescriptor {
        val builder = service.Builder()
        builder.setSession("ultra-client")
        builder.setMtu(1500)
        builder.addAddress("10.0.0.1", 32)
        applyRoutes(builder)
        applyDns(builder, antiDetectConfig.fakeDnsEnabled)
        applySplitTunnel(builder, antiDetectConfig.splitTunnelRules)
        builder.setBlocking(true)
        return builder.establish()
            ?: error("VpnService.Builder.establish() returned null — permission not granted?")
    }

    private fun applyRoutes(builder: VpnService.Builder) {
        builder.addRoute("0.0.0.0", 0)
        builder.addRoute("::", 0)
    }

    private fun applyDns(builder: VpnService.Builder, fakeDns: Boolean) {
        if (fakeDns) {
            builder.addDnsServer("198.18.0.3")
        } else {
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")
        }
    }

    private fun applySplitTunnel(builder: VpnService.Builder, rules: List<SplitTunnelRule>) {
        rules.filter { it.isExcluded }.forEach { rule ->
            try {
                builder.addDisallowedApplication(rule.appId)
            } catch (e: Exception) {
                android.util.Log.w("TunConfigurator", "Cannot exclude app ${rule.appId}: ${e.message}")
            }
        }
    }
}

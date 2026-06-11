package io.nikdmitryuk.ultraclient.android.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig

class TunConfigurator(
    private val service: VpnService,
) {
    companion object {
        private const val TAG = "TunConfigurator"
    }

    /** Per-app routing counts for diagnostics (allow-list vs legacy bypass). */
    data class PerAppRoutingStats(
        val mode: String,
        val allowedAttempted: Int,
        val allowedOk: Int,
        val allowedFail: Int,
        val legacyDisallowAttempted: Int,
        val legacyDisallowOk: Int,
        val legacyDisallowFail: Int,
    )

    fun establish(antiDetectConfig: AntiDetectConfig): ParcelFileDescriptor {
        val builder = service.Builder()
        builder.setSession("ultra-client")
        builder.setMtu(1500)
        builder.addAddress("10.0.0.1", 32)
        applyRoutes(builder)
        applyDns(builder, antiDetectConfig.fakeDnsEnabled)
        val perAppStats = applyPerAppRouting(builder, antiDetectConfig)
        logVpnBuilderSummary(antiDetectConfig)
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "A",
            location = "TunConfigurator.establish",
            message = "per_app_routing",
            data =
                mapOf(
                    "mode" to perAppStats.mode,
                    "allowedAttempted" to perAppStats.allowedAttempted,
                    "allowedOk" to perAppStats.allowedOk,
                    "allowedFail" to perAppStats.allowedFail,
                    "legacyDisallowAttempted" to perAppStats.legacyDisallowAttempted,
                    "legacyOk" to perAppStats.legacyDisallowOk,
                    "legacyFail" to perAppStats.legacyDisallowFail,
                    "fakeDns" to antiDetectConfig.fakeDnsEnabled,
                ),
        )
        // #endregion
        builder.setBlocking(true)
        val tun =
            builder.establish()
                ?: error("VpnService.Builder.establish() returned null — permission not granted?")
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "D",
            location = "TunConfigurator.establish",
            message = "tun_established",
            data =
                mapOf(
                    "fd" to tun.fd,
                    "mtu" to 1500,
                ),
        )
        // #endregion
        return tun
    }

    private fun applyRoutes(builder: VpnService.Builder) {
        builder.addRoute("0.0.0.0", 0)
        builder.addRoute("::", 0)
    }

    private fun applyDns(
        builder: VpnService.Builder,
        fakeDns: Boolean,
    ) {
        if (fakeDns) {
            builder.addDnsServer("198.18.0.3")
        } else {
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")
        }
    }

    /**
     * Preferred: explicit allow-list ([VpnAppRouteRule.throughVpn]) via [VpnService.Builder.addAllowedApplication].
     * Until settings are migrated from an older DB, falls back to legacy bypass list via [VpnService.Builder.addDisallowedApplication].
     */
    private fun applyPerAppRouting(
        builder: VpnService.Builder,
        anti: AntiDetectConfig,
    ): PerAppRoutingStats {
        val allowed = anti.vpnIncludedApps.filter { it.throughVpn }
        var allowedOk = 0
        var allowedFail = 0
        var legacyOk = 0
        var legacyFail = 0
        val mode: String
        when {
            allowed.isNotEmpty() -> {
                mode = "allow_list"
                allowed.forEach { rule ->
                    try {
                        builder.addAllowedApplication(rule.appId)
                        allowedOk++
                    } catch (e: Exception) {
                        allowedFail++
                        android.util.Log.w("TunConfigurator", "Cannot allow app ${rule.appId}: ${e.message}")
                    }
                }
            }
            anti.legacyBypassAppIds.isNotEmpty() -> {
                mode = "legacy_bypass"
                anti.legacyBypassAppIds.forEach { pkg ->
                    try {
                        builder.addDisallowedApplication(pkg)
                        legacyOk++
                    } catch (e: Exception) {
                        legacyFail++
                        android.util.Log.w("TunConfigurator", "Cannot disallow legacy bypass $pkg: ${e.message}")
                    }
                }
            }
            else -> {
                mode = "full_tunnel"
            }
        }
        return PerAppRoutingStats(
            mode = mode,
            allowedAttempted = allowed.size,
            allowedOk = allowedOk,
            allowedFail = allowedFail,
            legacyDisallowAttempted = anti.legacyBypassAppIds.size,
            legacyDisallowOk = legacyOk,
            legacyDisallowFail = legacyFail,
        )
    }

    private fun logVpnBuilderSummary(anti: AntiDetectConfig) {
        val dns =
            if (anti.fakeDnsEnabled) {
                "198.18.0.3 (fake)"
            } else {
                "1.1.1.1, 8.8.8.8 (plain)"
            }
        val allowed = anti.vpnIncludedApps.count { it.throughVpn }
        val legacy = anti.legacyBypassAppIds.size
        val mode =
            when {
                allowed > 0 -> "addAllowedApplication x $allowed"
                legacy > 0 -> "addDisallowedApplication x $legacy (legacy)"
                else -> "no per-app filter (full tunnel)"
            }
        Log.i(TAG, "VPN Builder DNS=$dns perApp=$mode fakeDnsFlag=${anti.fakeDnsEnabled}")
    }
}

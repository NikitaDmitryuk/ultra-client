package io.nikdmitryuk.ultraclient.android.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import java.lang.reflect.Method

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

    fun establishForSingBox(
        tunOptions: Any,
        antiDetectConfig: AntiDetectConfig,
    ): ParcelFileDescriptor {
        val builder = service.Builder()
        builder.setSession("ultra-client")
        builder.setMtu(callInt(tunOptions, "getMTU", "GetMTU") ?: 1500)

        val addresses =
            routePrefixes(tunOptions, "getInet4Address", "GetInet4Address") +
                routePrefixes(tunOptions, "getInet6Address", "GetInet6Address")
        if (addresses.isEmpty()) {
            builder.addAddress("172.19.0.1", 30)
        } else {
            addresses.forEach { prefix ->
                builder.addAddress(prefix.address, prefix.prefix)
            }
        }

        val routes =
            routePrefixes(tunOptions, "getInet4RouteRange", "GetInet4RouteRange") +
                routePrefixes(tunOptions, "getInet6RouteRange", "GetInet6RouteRange")
        if (routes.isEmpty()) {
            applyRoutes(builder)
        } else {
            routes.forEach { prefix ->
                builder.addRoute(prefix.address, prefix.prefix)
            }
        }

        val dnsServer = callStringBox(tunOptions, "getDNSServerAddress", "GetDNSServerAddress")
        if (dnsServer.isNullOrBlank()) {
            applyDns(builder, antiDetectConfig.fakeDnsEnabled)
        } else {
            builder.addDnsServer(dnsServer)
        }

        val perAppStats = applyPerAppRouting(builder, antiDetectConfig)
        logVpnBuilderSummary(antiDetectConfig)
        builder.setBlocking(true)
        val tun =
            builder.establish()
                ?: error("VpnService.Builder.establish() returned null — permission not granted?")
        AgentDebugLog.log(
            hypothesisId = "S",
            location = "TunConfigurator.establishForSingBox",
            message = "singbox_tun_established",
            data =
                mapOf(
                    "fd" to tun.fd,
                    "addresses" to addresses.joinToString { it.toString() },
                    "routes" to routes.size,
                    "dns" to (dnsServer ?: "platform"),
                    "perAppMode" to perAppStats.mode,
                ),
        )
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

    private data class Prefix(
        val address: String,
        val prefix: Int,
    ) {
        override fun toString(): String = "$address/$prefix"
    }

    private fun routePrefixes(
        target: Any,
        vararg methodNames: String,
    ): List<Prefix> {
        val iterator = callAny(target, *methodNames) ?: return emptyList()
        val hasNext = findMethod(iterator, "hasNext", "HasNext") ?: return emptyList()
        val next = findMethod(iterator, "next", "Next") ?: return emptyList()
        val result = mutableListOf<Prefix>()
        while ((hasNext.invoke(iterator) as? Boolean) == true) {
            val prefix = next.invoke(iterator) ?: break
            val address = callString(prefix, "address", "Address") ?: continue
            val prefixBits = callInt(prefix, "prefix", "Prefix") ?: continue
            result += Prefix(address, prefixBits)
        }
        return result
    }

    private fun callStringBox(
        target: Any,
        vararg methodNames: String,
    ): String? {
        val box = callAny(target, *methodNames) ?: return null
        return box.javaClass.fields
            .firstOrNull { it.name.equals("value", ignoreCase = true) }
            ?.get(box) as? String
            ?: callString(box, "getValue", "GetValue", "value", "Value")
            ?: box.toString().takeIf { it.isNotBlank() && !it.contains("@") }
    }

    private fun callAny(
        target: Any,
        vararg methodNames: String,
    ): Any? = findMethod(target, *methodNames)?.invoke(target)

    private fun callString(
        target: Any,
        vararg methodNames: String,
    ): String? = callAny(target, *methodNames) as? String

    private fun callInt(
        target: Any,
        vararg methodNames: String,
    ): Int? = (callAny(target, *methodNames) as? Number)?.toInt()

    private fun findMethod(
        target: Any,
        vararg names: String,
    ): Method? =
        names.firstNotNullOfOrNull { name ->
            target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
        }
}

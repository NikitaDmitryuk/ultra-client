package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AntiDetectConfig(
    val killSwitchEnabled: Boolean = false,
    val fakeDnsEnabled: Boolean = true,
    val randomPortEnabled: Boolean = true,
    /**
     * Apps that use the VPN tunnel. Stored in DB as JSON (only entries with [VpnAppRouteRule.throughVpn] == true).
     * When non-empty, [android.net.VpnService.Builder.addAllowedApplication] is used per entry.
     */
    val vpnIncludedApps: List<VpnAppRouteRule> = emptyList(),
    /**
     * Filled when reading legacy DB where we only stored "bypass" apps; used for migration UI and,
     * until the user saves new settings, for [android.net.VpnService.Builder.addDisallowedApplication].
     */
    val legacyBypassAppIds: List<String> = emptyList(),
)

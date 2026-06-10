package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VpnAppRouteRule(
    val appId: String,
    val appName: String,
    /** If true, this app’s traffic is routed through the VPN tunnel (see [android.net.VpnService.Builder.addAllowedApplication]). */
    val throughVpn: Boolean = false,
)

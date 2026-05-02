package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SplitTunnelRule(
    val appId: String,
    val appName: String,
    val isExcluded: Boolean
)

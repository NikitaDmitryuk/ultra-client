package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AntiDetectConfig(
    val killSwitchEnabled: Boolean = false,
    val fakeDnsEnabled: Boolean = true,
    val randomPortEnabled: Boolean = true,
    val splitTunnelRules: List<SplitTunnelRule> = emptyList(),
)

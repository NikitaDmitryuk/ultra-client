package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VpnProfile(
    val id: String,
    val name: String,
    val rawUrl: String,
    val config: VlessConfig,
    val isActive: Boolean = false,
    val createdAt: Long,
)

package io.nikdmitryuk.ultraclient.domain.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import kotlinx.coroutines.flow.Flow

interface VpnEngine {
    val state: Flow<VpnState>

    suspend fun connect(
        config: VlessConfig,
        antiDetect: AntiDetectConfig,
    ): Result<Unit>

    suspend fun disconnect(): Result<Unit>

    fun isConnected(): Boolean
}

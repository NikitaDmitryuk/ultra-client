package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine
import kotlinx.coroutines.flow.Flow

expect class PlatformVpnEngine : VpnEngine {
    override val state: Flow<VpnState>
    override suspend fun connect(config: VlessConfig, antiDetect: AntiDetectConfig): Result<Unit>
    override suspend fun disconnect(): Result<Unit>
    override fun isConnected(): Boolean
}

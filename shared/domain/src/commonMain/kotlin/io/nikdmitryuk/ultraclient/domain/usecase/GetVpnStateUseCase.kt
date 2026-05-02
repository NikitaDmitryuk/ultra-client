package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.model.VpnState
import io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine
import kotlinx.coroutines.flow.Flow

class GetVpnStateUseCase(private val vpnEngine: VpnEngine) {
    operator fun invoke(): Flow<VpnState> = vpnEngine.state
}

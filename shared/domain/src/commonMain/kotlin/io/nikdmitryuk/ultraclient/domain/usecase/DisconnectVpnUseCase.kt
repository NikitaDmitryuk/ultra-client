package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine

class DisconnectVpnUseCase(
    private val vpnEngine: VpnEngine,
) {
    suspend operator fun invoke(): Result<Unit> = vpnEngine.disconnect()
}

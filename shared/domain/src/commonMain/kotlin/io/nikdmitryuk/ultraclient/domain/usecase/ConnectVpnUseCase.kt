package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository
import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository
import io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine

class ConnectVpnUseCase(
    private val profileRepository: VpnProfileRepository,
    private val antiDetectRepository: AntiDetectRepository,
    private val vpnEngine: VpnEngine
) {
    suspend operator fun invoke(profileId: String): Result<Unit> = runCatching {
        val profile = profileRepository.getById(profileId)
            ?: error("Profile $profileId not found")
        val antiDetect = antiDetectRepository.get()
        vpnEngine.connect(profile.config, antiDetect).getOrThrow()
    }
}

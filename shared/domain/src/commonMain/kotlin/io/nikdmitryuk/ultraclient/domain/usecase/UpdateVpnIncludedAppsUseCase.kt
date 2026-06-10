package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.model.VpnAppRouteRule
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository

class UpdateVpnIncludedAppsUseCase(
    private val repository: AntiDetectRepository,
) {
    suspend operator fun invoke(rules: List<VpnAppRouteRule>): Result<Unit> =
        runCatching {
            val current = repository.get()
            val included = rules.filter { it.throughVpn }
            repository.update(
                current.copy(
                    vpnIncludedApps = included,
                    legacyBypassAppIds = emptyList(),
                ),
            )
        }
}

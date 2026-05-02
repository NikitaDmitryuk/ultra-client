package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository

class SetActiveProfileUseCase(private val repository: VpnProfileRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = runCatching {
        repository.setActive(id)
    }
}

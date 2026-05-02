package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository

class DeleteProfileUseCase(
    private val repository: VpnProfileRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        runCatching {
            repository.delete(id)
        }
}

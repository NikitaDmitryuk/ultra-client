package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository

class UpdateAntiDetectUseCase(
    private val repository: AntiDetectRepository,
) {
    suspend operator fun invoke(config: AntiDetectConfig): Result<Unit> =
        runCatching {
            repository.update(config)
        }
}

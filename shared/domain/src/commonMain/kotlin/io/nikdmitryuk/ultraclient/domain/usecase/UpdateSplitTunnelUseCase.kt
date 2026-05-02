package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.model.SplitTunnelRule
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository

class UpdateSplitTunnelUseCase(private val repository: AntiDetectRepository) {
    suspend operator fun invoke(rules: List<SplitTunnelRule>): Result<Unit> = runCatching {
        val current = repository.get()
        repository.update(current.copy(splitTunnelRules = rules))
    }
}

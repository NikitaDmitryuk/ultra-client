package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository
import kotlinx.coroutines.flow.Flow

class GetProfilesUseCase(
    private val repository: VpnProfileRepository,
) {
    operator fun invoke(): Flow<List<VpnProfile>> = repository.observeAll()
}

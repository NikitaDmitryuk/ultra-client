package io.nikdmitryuk.ultraclient.domain.repository

import io.nikdmitryuk.ultraclient.domain.model.ExitSelectionState
import io.nikdmitryuk.ultraclient.domain.model.VpnProfile

interface ExitLocationRepository {
    suspend fun getLocations(profile: VpnProfile): Result<ExitSelectionState>

    suspend fun selectLocation(
        profile: VpnProfile,
        exitId: String?,
    ): Result<ExitSelectionState>
}

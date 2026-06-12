package io.nikdmitryuk.ultraclient.data.repository

import io.nikdmitryuk.ultraclient.data.remote.ClientExitApi
import io.nikdmitryuk.ultraclient.domain.model.ExitSelectionState
import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import io.nikdmitryuk.ultraclient.domain.repository.ExitLocationRepository

class ExitLocationRepositoryImpl(
    private val api: ClientExitApi,
) : ExitLocationRepository {
    override suspend fun getLocations(profile: VpnProfile): Result<ExitSelectionState> =
        runCatching {
            val apiBaseUrl = profile.config.apiBaseUrl.trim()
            if (apiBaseUrl.isEmpty()) error("Location selection unavailable for this profile. Re-import profile from Mini App.")
            api.listExits(apiBaseUrl, profile.config.uuid)
        }

    override suspend fun selectLocation(
        profile: VpnProfile,
        exitId: String?,
    ): Result<ExitSelectionState> =
        runCatching {
            val apiBaseUrl = profile.config.apiBaseUrl.trim()
            if (apiBaseUrl.isEmpty()) error("Location selection unavailable for this profile. Re-import profile from Mini App.")
            api.setExitSelection(apiBaseUrl, profile.config.uuid, exitId)
        }
}

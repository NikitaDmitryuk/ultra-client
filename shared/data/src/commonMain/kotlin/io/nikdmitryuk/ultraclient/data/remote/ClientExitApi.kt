package io.nikdmitryuk.ultraclient.data.remote

import io.nikdmitryuk.ultraclient.domain.model.ExitSelectionState

expect class ClientExitApi() {
    suspend fun listExits(
        apiBaseUrl: String,
        token: String,
    ): ExitSelectionState

    suspend fun setExitSelection(
        apiBaseUrl: String,
        token: String,
        exitId: String?,
    ): ExitSelectionState
}

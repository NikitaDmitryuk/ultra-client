package io.nikdmitryuk.ultraclient.data.remote

import io.nikdmitryuk.ultraclient.domain.model.ExitSelectionState

actual class ClientExitApi actual constructor() {
    actual suspend fun listExits(
        apiBaseUrl: String,
        token: String,
    ): ExitSelectionState = error("Location selection is not implemented on iOS yet")

    actual suspend fun setExitSelection(
        apiBaseUrl: String,
        token: String,
        exitId: String?,
    ): ExitSelectionState = error("Location selection is not implemented on iOS yet")
}

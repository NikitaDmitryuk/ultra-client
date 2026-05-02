package io.nikdmitryuk.ultraclient.domain.model

sealed class VpnState {
    object Disconnected : VpnState()

    object Connecting : VpnState()

    data class Connected(
        val serverAddress: String,
        val connectedAt: Long,
    ) : VpnState()

    data class Error(
        val message: String,
    ) : VpnState()
}

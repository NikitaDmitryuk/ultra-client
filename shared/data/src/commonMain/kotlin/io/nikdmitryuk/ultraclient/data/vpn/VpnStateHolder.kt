package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object VpnStateHolder {
    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val state: StateFlow<VpnState> = _state

    fun emit(newState: VpnState) {
        _state.value = newState
    }
}

package io.nikdmitryuk.ultraclient.presentation.screen.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import io.nikdmitryuk.ultraclient.domain.usecase.ConnectVpnUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.DisconnectVpnUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.GetProfilesUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.GetVpnStateUseCase
import io.nikdmitryuk.ultraclient.presentation.platform.measureDnsResolveMs
import io.nikdmitryuk.ultraclient.presentation.platform.measurePingMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val vpnState: VpnState = VpnState.Disconnected,
    val activeProfile: VpnProfile? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pingLatencyMs: Long? = null,
    val dnsResolveLatencyMs: Long? = null,
)

class HomeScreenModel(
    private val connectVpnUseCase: ConnectVpnUseCase,
    private val disconnectVpnUseCase: DisconnectVpnUseCase,
    private val getVpnStateUseCase: GetVpnStateUseCase,
    private val getProfilesUseCase: GetProfilesUseCase,
) : ScreenModel {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var pingJob: Job? = null

    init {
        screenModelScope.launch {
            combine(
                getVpnStateUseCase(),
                getProfilesUseCase(),
            ) { state, profiles ->
                Pair(state, profiles.firstOrNull { it.isActive })
            }.collect { (state, active) ->
                _uiState.update { current ->
                    current.copy(
                        vpnState = state,
                        activeProfile = active,
                        errorMessage = if (state is VpnState.Error && current.vpnState !is VpnState.Error) {
                            state.message
                        } else {
                            current.errorMessage
                        },
                    )
                }
                if (state is VpnState.Connected) {
                    if (pingJob?.isActive != true) startPingLoop()
                } else {
                    pingJob?.cancel()
                    pingJob = null
                    _uiState.update { it.copy(pingLatencyMs = null, dnsResolveLatencyMs = null) }
                }
            }
        }
    }

    private fun startPingLoop() {
        pingJob = screenModelScope.launch {
            while (true) {
                val tcp = measurePingMs()
                val dns = measureDnsResolveMs()
                _uiState.update {
                    it.copy(pingLatencyMs = tcp, dnsResolveLatencyMs = dns)
                }
                delay(5_000)
            }
        }
    }

    fun toggleVpn(requestPermission: ((Boolean) -> Unit) -> Unit = { it(true) }) {
        val current = _uiState.value
        when (current.vpnState) {
            is VpnState.Disconnected, is VpnState.Error -> {
                val profileId =
                    current.activeProfile?.id ?: run {
                        _uiState.update { it.copy(errorMessage = "No profile selected") }
                        return
                    }
                requestPermission { granted ->
                    if (granted) connectVpn(profileId)
                }
            }
            is VpnState.Connected, is VpnState.Connecting -> disconnectVpn()
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private fun connectVpn(profileId: String) {
        screenModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            connectVpnUseCase(profileId).onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun disconnectVpn() {
        screenModelScope.launch {
            disconnectVpnUseCase().onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }
}

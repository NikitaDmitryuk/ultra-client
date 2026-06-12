package io.nikdmitryuk.ultraclient.presentation.screen.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.ExitSelectionState
import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import io.nikdmitryuk.ultraclient.domain.model.VpnAppRouteRule
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository
import io.nikdmitryuk.ultraclient.domain.repository.ExitLocationRepository
import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository
import io.nikdmitryuk.ultraclient.domain.usecase.UpdateVpnIncludedAppsUseCase
import io.nikdmitryuk.ultraclient.presentation.platform.InstalledAppsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val config: AntiDetectConfig = AntiDetectConfig(),
    val activeProfile: VpnProfile? = null,
    val availableApps: List<VpnAppRouteRule> = emptyList(),
    val exitSelection: ExitSelectionState? = null,
    val isLoadingApps: Boolean = false,
    val isLoadingLocations: Boolean = false,
    val locationMessage: String? = null,
)

class SettingsScreenModel(
    private val antiDetectRepository: AntiDetectRepository,
    private val vpnProfileRepository: VpnProfileRepository,
    private val exitLocationRepository: ExitLocationRepository,
    private val updateVpnIncludedAppsUseCase: UpdateVpnIncludedAppsUseCase,
    private val installedAppsProvider: InstalledAppsProvider,
) : ScreenModel {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        screenModelScope.launch {
            antiDetectRepository.observe().collect { config ->
                _uiState.update { it.copy(config = config) }
            }
        }
        screenModelScope.launch {
            vpnProfileRepository.observeAll().collect { profiles ->
                _uiState.update { it.copy(activeProfile = profiles.firstOrNull { p -> p.isActive }) }
            }
        }
    }

    fun loadInstalledApps() {
        screenModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = installedAppsProvider.getInstalledApps()
            val config = antiDetectRepository.get()
            val merged = mergeVpnApps(apps, config)
            _uiState.update { it.copy(availableApps = merged, isLoadingApps = false) }
            if (config.legacyBypassAppIds.isNotEmpty() && merged.any { it.throughVpn }) {
                updateVpnIncludedAppsUseCase(merged.filter { it.throughVpn })
            }
        }
    }

    fun toggleThroughVpn(
        appId: String,
        throughVpn: Boolean,
    ) {
        val current = _uiState.value
        val updated =
            current.availableApps.map {
                if (it.appId == appId) it.copy(throughVpn = throughVpn) else it
            }
        _uiState.update { it.copy(availableApps = updated) }
        screenModelScope.launch {
            updateVpnIncludedAppsUseCase(updated.filter { it.throughVpn })
        }
    }

    fun loadLocations() {
        val profile = _uiState.value.activeProfile
        if (profile == null) {
            _uiState.update { it.copy(exitSelection = null, locationMessage = "No active profile selected") }
            return
        }
        screenModelScope.launch {
            _uiState.update { it.copy(isLoadingLocations = true, locationMessage = null) }
            exitLocationRepository.getLocations(profile)
                .onSuccess { selection ->
                    _uiState.update { it.copy(exitSelection = selection, isLoadingLocations = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            exitSelection = null,
                            isLoadingLocations = false,
                            locationMessage = e.message ?: "Location selection unavailable",
                        )
                    }
                }
        }
    }

    fun selectLocation(exitId: String?) {
        val profile = _uiState.value.activeProfile ?: return
        screenModelScope.launch {
            _uiState.update { it.copy(isLoadingLocations = true, locationMessage = null) }
            exitLocationRepository.selectLocation(profile, exitId)
                .onSuccess { selection ->
                    _uiState.update {
                        it.copy(
                            exitSelection = selection,
                            isLoadingLocations = false,
                            locationMessage = "Location updated. Existing connections may reconnect briefly.",
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingLocations = false,
                            locationMessage = e.message ?: "Could not update location",
                        )
                    }
                }
        }
    }
}

/** Default: opt-in to VPN. Legacy DB: apps that were not on the old bypass list get VPN. */
private fun mergeVpnApps(
    apps: List<VpnAppRouteRule>,
    config: AntiDetectConfig,
): List<VpnAppRouteRule> =
    apps.map { app ->
        val saved = config.vpnIncludedApps.find { it.appId == app.appId }
        val through =
            when {
                saved != null -> true
                config.legacyBypassAppIds.contains(app.appId) -> false
                config.legacyBypassAppIds.isNotEmpty() -> true
                else -> false
            }
        VpnAppRouteRule(
            appId = app.appId,
            appName = saved?.appName ?: app.appName,
            throughVpn = through,
        )
    }

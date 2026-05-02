package io.nikdmitryuk.ultraclient.presentation.screen.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.SplitTunnelRule
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository
import io.nikdmitryuk.ultraclient.domain.usecase.UpdateAntiDetectUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.UpdateSplitTunnelUseCase
import io.nikdmitryuk.ultraclient.presentation.platform.InstalledAppsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val config: AntiDetectConfig = AntiDetectConfig(),
    val availableApps: List<SplitTunnelRule> = emptyList(),
    val isLoadingApps: Boolean = false,
)

class SettingsScreenModel(
    private val antiDetectRepository: AntiDetectRepository,
    private val updateAntiDetectUseCase: UpdateAntiDetectUseCase,
    private val updateSplitTunnelUseCase: UpdateSplitTunnelUseCase,
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
    }

    fun loadInstalledApps() {
        screenModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = installedAppsProvider.getInstalledApps()
            val rules = _uiState.value.config.splitTunnelRules
            val merged =
                apps.map { app ->
                    rules.find { it.appId == app.appId } ?: app
                }
            _uiState.update { it.copy(availableApps = merged, isLoadingApps = false) }
        }
    }

    fun toggleKillSwitch(enabled: Boolean) = updateConfig { it.copy(killSwitchEnabled = enabled) }

    fun toggleFakeDns(enabled: Boolean) = updateConfig { it.copy(fakeDnsEnabled = enabled) }

    fun toggleRandomPort(enabled: Boolean) = updateConfig { it.copy(randomPortEnabled = enabled) }

    fun toggleAppExclusion(
        appId: String,
        excluded: Boolean,
    ) {
        val current = _uiState.value
        val updated =
            current.availableApps.map {
                if (it.appId == appId) it.copy(isExcluded = excluded) else it
            }
        _uiState.update { it.copy(availableApps = updated) }
        screenModelScope.launch {
            updateSplitTunnelUseCase(updated.filter { it.isExcluded })
        }
    }

    private fun updateConfig(transform: (AntiDetectConfig) -> AntiDetectConfig) {
        screenModelScope.launch {
            updateAntiDetectUseCase(transform(_uiState.value.config))
        }
    }
}

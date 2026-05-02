package io.nikdmitryuk.ultraclient.presentation.screen.profiles

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import io.nikdmitryuk.ultraclient.domain.usecase.DeleteProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.GetProfilesUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.ImportProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.SetActiveProfileUseCase
import io.nikdmitryuk.ultraclient.presentation.platform.ClipboardReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<VpnProfile> = emptyList(),
    val isImporting: Boolean = false,
    val importError: String? = null
)

class ProfilesScreenModel(
    private val getProfilesUseCase: GetProfilesUseCase,
    private val importProfileUseCase: ImportProfileUseCase,
    private val deleteProfileUseCase: DeleteProfileUseCase,
    private val setActiveProfileUseCase: SetActiveProfileUseCase,
    private val clipboardReader: ClipboardReader
) : ScreenModel {

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    init {
        screenModelScope.launch {
            getProfilesUseCase().collect { profiles ->
                _uiState.update { it.copy(profiles = profiles) }
            }
        }
    }

    fun importFromClipboard() {
        val text = clipboardReader.readText()
        if (text.isNullOrBlank()) {
            _uiState.update { it.copy(importError = "Clipboard is empty") }
            return
        }
        screenModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            importProfileUseCase(text.trim()).onFailure { e ->
                _uiState.update { it.copy(importError = e.message ?: "Failed to parse config") }
            }
            _uiState.update { it.copy(isImporting = false) }
        }
    }

    fun deleteProfile(id: String) {
        screenModelScope.launch { deleteProfileUseCase(id) }
    }

    fun setActiveProfile(id: String) {
        screenModelScope.launch { setActiveProfileUseCase(id) }
    }

    fun clearError() = _uiState.update { it.copy(importError = null) }
}

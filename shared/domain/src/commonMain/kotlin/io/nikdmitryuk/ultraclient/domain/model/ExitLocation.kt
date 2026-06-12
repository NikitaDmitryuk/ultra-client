package io.nikdmitryuk.ultraclient.domain.model

data class ExitLocation(
    val id: String,
    val displayName: String,
    val countryCode: String = "",
    val countryName: String = "",
    val city: String = "",
    val reachable: Boolean = false,
    val latencyMs: Long? = null,
    val selected: Boolean = false,
    val effective: Boolean = false,
)

data class ExitSelectionState(
    val selectedExitId: String? = null,
    val effectiveExitId: String? = null,
    val exits: List<ExitLocation> = emptyList(),
)

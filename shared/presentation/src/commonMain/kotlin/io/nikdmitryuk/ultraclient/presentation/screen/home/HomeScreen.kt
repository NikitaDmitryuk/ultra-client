package io.nikdmitryuk.ultraclient.presentation.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.nikdmitryuk.ultraclient.presentation.components.PowerButton
import io.nikdmitryuk.ultraclient.presentation.components.VpnStatusIndicator
import io.nikdmitryuk.ultraclient.presentation.platform.LocalVpnPermissionRequester
import io.nikdmitryuk.ultraclient.presentation.screen.profiles.ProfilesScreen
import io.nikdmitryuk.ultraclient.presentation.screen.settings.SettingsScreen

class HomeScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model = koinScreenModel<HomeScreenModel>()
        val state by model.uiState.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbar = remember { SnackbarHostState() }
        val requestVpnPermission = LocalVpnPermissionRequester.current

        LaunchedEffect(state.errorMessage) {
            state.errorMessage?.let {
                snackbar.showSnackbar(it)
                model.clearError()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {},
                    actions = {
                        IconButton(onClick = { navigator.push(SettingsScreen()) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "ultra-client",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                VpnStatusIndicator(state = state.vpnState)
                run {
                    val parts =
                        buildList {
                            state.pingLatencyMs?.let { add("TCP ~ ${it} ms") }
                            when {
                                state.dnsResolveLatencyMs != null ->
                                    add("Name ~ ${state.dnsResolveLatencyMs} ms")
                                state.pingLatencyMs != null ->
                                    add("name lookup failed")
                                else -> { }
                            }
                        }
                    if (parts.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(48.dp))
                PowerButton(
                    state = state.vpnState,
                    onClick = { model.toggleVpn(requestVpnPermission) },
                )
                Spacer(Modifier.height(32.dp))
                state.activeProfile?.let { profile ->
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } ?: Text(
                    text = "No profile selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { navigator.push(ProfilesScreen()) }) {
                    Text("Manage profiles")
                }
            }
        }
    }
}

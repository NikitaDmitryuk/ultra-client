package io.nikdmitryuk.ultraclient.presentation.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model = koinScreenModel<SettingsScreenModel>()
        val state by model.uiState.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) { model.loadInstalledApps() }
        LaunchedEffect(state.activeProfile?.id) { model.loadLocations() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("VPN app routing") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                item {
                    Text(
                        "Routing",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }

                item {
                    ConnectivityHintsCard()
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        "Location",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    LocationSelector(state, model)
                }

                if (state.availableApps.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text(
                            "Apps using VPN",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Text(
                            "Check the apps that should use the VPN tunnel. All other apps keep your normal connection. You need at least one app to connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    items(state.availableApps, key = { it.appId }) { rule ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = rule.throughVpn,
                                onCheckedChange = { model.toggleThroughVpn(rule.appId, it) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.appName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    rule.appId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item {
                        VpnAppsTroubleshootFooter()
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSelector(
    state: SettingsUiState,
    model: SettingsScreenModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.locationMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isLoadingLocations) {
            CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
        }
        val selection = state.exitSelection
        if (selection != null) {
            TextButton(
                onClick = { model.selectLocation(null) },
                enabled = !state.isLoadingLocations && selection.selectedExitId != null,
            ) {
                Text(if (selection.selectedExitId == null) "Auto selected" else "Auto")
            }
            selection.exits.forEach { exit ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(exit.displayName, style = MaterialTheme.typography.bodyMedium)
                        val details =
                            buildList {
                                if (exit.selected) add("selected")
                                if (exit.effective) add("effective")
                                add(if (exit.reachable) "reachable" else "unreachable")
                                exit.latencyMs?.let { add("${it} ms") }
                            }.joinToString(" · ")
                        Text(
                            details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { model.selectLocation(exit.id) },
                        enabled = !state.isLoadingLocations && !exit.selected,
                    ) {
                        Text(if (exit.selected) "Selected" else "Choose")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectivityHintsCard() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Connectivity troubleshooting",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            HintLine(
                "Allow-listed apps only go through the VPN. Everything else uses your normal connection — it may look “online” while checked apps are not.",
            )
            HintLine(
                "Android apps can detect that their own traffic uses a VPN network. Keep sensitive apps unchecked if they should see your normal IP.",
            )
            HintLine(
                "DNS is handled inside the tunnel automatically. If domains fail but IP addresses work, check Private DNS on the device first.",
            )
            HintLine(
                "On the device: Settings → Network & Internet → Private DNS → Off or Automatic (wording varies by OEM).",
            )
            HintLine(
                "Home shows TCP to 1.1.1.1 and a hostname check — if TCP works but the name line fails, DNS in the tunnel is the likely issue.",
            )
            HintLine(
                "Those numbers are from this VPN app only, not from your checked packages.",
            )
        }
    }
}

@Composable
private fun VpnAppsTroubleshootFooter() {
    Text(
        "Still stuck? Capture logcat while reproducing (filters: TunConfigurator, SingBoxBridge, sing-box).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun HintLine(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

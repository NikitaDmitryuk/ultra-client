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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model = getScreenModel<SettingsScreenModel>()
        val state by model.uiState.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) { model.loadInstalledApps() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Anti-detect settings") },
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
                        "Protection",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                item {
                    ToggleRow(
                        "Kill Switch",
                        "Block all traffic if VPN drops",
                        state.config.killSwitchEnabled,
                    ) { model.toggleKillSwitch(it) }
                }
                item {
                    ToggleRow(
                        "Fake DNS",
                        "Route DNS through tunnel to prevent leaks",
                        state.config.fakeDnsEnabled,
                    ) { model.toggleFakeDns(it) }
                }
                item {
                    ToggleRow("Random ports", "Use random local ports to prevent fingerprinting", state.config.randomPortEnabled) {
                        model.toggleRandomPort(it)
                    }
                }

                item {
                    ConnectivityHintsCard()
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
                "Try turning off Fake DNS, reconnect, then test again.",
            )
            HintLine(
                "On the device: Settings → Network & Internet → Private DNS → Off or Automatic (wording varies by OEM).",
            )
            HintLine(
                "If something works by IP address but not by domain name, DNS inside the tunnel is failing — adjust Fake DNS / Private DNS first.",
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
        "Still stuck? Capture logcat while reproducing (filters: TunConfigurator, XrayBridge, Xray).",
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

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

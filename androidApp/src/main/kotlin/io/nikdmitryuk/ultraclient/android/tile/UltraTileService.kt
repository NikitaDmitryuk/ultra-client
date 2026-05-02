package io.nikdmitryuk.ultraclient.android.tile

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.nikdmitryuk.ultraclient.android.MainActivity
import io.nikdmitryuk.ultraclient.android.vpn.UltraVpnService
import io.nikdmitryuk.ultraclient.data.vpn.VpnStateHolder
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository
import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

class UltraTileService : TileService() {

    private val profileRepository: VpnProfileRepository by inject()
    private val antiDetectRepository: AntiDetectRepository by inject()
    private val json = Json { ignoreUnknownKeys = true }

    private var listenScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        listenScope = scope
        VpnStateHolder.state
            .onEach { syncTile(it) }
            .launchIn(scope)
    }

    override fun onStopListening() {
        super.onStopListening()
        listenScope?.cancel()
        listenScope = null
    }

    override fun onClick() {
        super.onClick()
        when (VpnStateHolder.state.value) {
            is VpnState.Connected, is VpnState.Connecting -> disconnect()
            is VpnState.Disconnected, is VpnState.Error -> connectOrOpenApp()
        }
    }

    private fun disconnect() {
        startService(
            Intent(this, UltraVpnService::class.java)
                .apply { action = UltraVpnService.ACTION_DISCONNECT }
        )
    }

    private fun connectOrOpenApp() {
        // VPN permission not yet granted — must show system dialog from an Activity
        if (VpnService.prepare(this) != null) {
            openApp()
            return
        }
        // Permission already granted — start tunnel directly
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val profile = profileRepository.getActive()
            if (profile == null) {
                openApp()
                return@launch
            }
            val antiDetect = antiDetectRepository.get()
            startService(
                Intent(this@UltraTileService, UltraVpnService::class.java).apply {
                    action = UltraVpnService.ACTION_CONNECT
                    putExtra(
                        UltraVpnService.EXTRA_VLESS_CONFIG,
                        json.encodeToString(VlessConfig.serializer(), profile.config)
                    )
                    putExtra(
                        UltraVpnService.EXTRA_ANTI_DETECT,
                        json.encodeToString(AntiDetectConfig.serializer(), antiDetect)
                    )
                }
            )
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun syncTile(state: VpnState) {
        val tile = qsTile ?: return
        when (state) {
            is VpnState.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = state.serverAddress
                }
            }
            is VpnState.Connecting -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Connecting…"
                }
            }
            is VpnState.Disconnected -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Off"
                }
            }
            is VpnState.Error -> {
                tile.state = Tile.STATE_UNAVAILABLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Error"
                }
            }
        }
        tile.updateTile()
    }
}

package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NETunnelProviderProtocol
import platform.NetworkExtension.NETunnelProviderSession
import kotlin.coroutines.resume

actual class PlatformVpnEngine : VpnEngine {
    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    actual override val state: Flow<VpnState> = _state

    actual override suspend fun connect(
        config: VlessConfig,
        antiDetect: AntiDetectConfig,
    ): Result<Unit> =
        runCatching {
            _state.value = VpnState.Connecting
            val configJson = Json.encodeToString(VlessConfig.serializer(), config)
            val antiDetectJson = Json.encodeToString(AntiDetectConfig.serializer(), antiDetect)
            val manager = loadOrCreateManager()
            val session = manager.connection as NETunnelProviderSession
            session.startTunnelWithOptions(
                mapOf("config" to configJson, "anti_detect" to antiDetectJson),
            ) { error ->
                if (error != null) {
                    _state.value = VpnState.Error(error.localizedDescription)
                } else {
                    _state.value = VpnState.Connected(config.address, currentEpochMillis())
                }
            }
        }

    actual override suspend fun disconnect(): Result<Unit> =
        runCatching {
            val manager = loadOrCreateManager()
            val session = manager.connection as NETunnelProviderSession
            session.stopTunnel()
            _state.value = VpnState.Disconnected
        }

    actual override fun isConnected(): Boolean = _state.value is VpnState.Connected

    private suspend fun loadOrCreateManager(): NETunnelProviderManager =
        suspendCancellableCoroutine { cont ->
            NETunnelProviderManager.loadAllFromPreferencesWithCompletionHandler { managers, _ ->
                val existing = managers?.firstOrNull() as? NETunnelProviderManager
                cont.resume(existing ?: createNewManager())
            }
        }

    private fun createNewManager(): NETunnelProviderManager {
        val manager = NETunnelProviderManager()
        val proto = NETunnelProviderProtocol()
        proto.providerBundleIdentifier = "io.nikdmitryuk.ultraclient.NetworkExtension"
        proto.serverAddress = "ultra-client"
        manager.protocolConfiguration = proto
        manager.localizedDescription = "ultra-client"
        manager.isEnabled = true
        manager.saveToPreferencesWithCompletionHandler { _ -> }
        return manager
    }

    private fun currentEpochMillis(): Long =
        platform.Foundation
            .NSDate()
            .timeIntervalSince1970
            .toLong() * 1000
}

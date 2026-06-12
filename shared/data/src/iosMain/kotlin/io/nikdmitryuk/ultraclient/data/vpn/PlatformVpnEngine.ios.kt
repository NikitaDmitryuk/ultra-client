package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import platform.Foundation.NSError
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NETunnelProviderProtocol
import platform.NetworkExtension.NETunnelProviderSession
import kotlin.coroutines.resume

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
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
            memScoped {
                val errorRef = alloc<ObjCObjectVar<NSError?>>()
                val started =
                    session.startTunnelWithOptions(
                        mapOf("config" to configJson, "anti_detect" to antiDetectJson),
                        andReturnError = errorRef.ptr,
                    )
                val error = errorRef.value
                if (!started || error != null) {
                    _state.value = VpnState.Error(error?.localizedDescription ?: "Failed to start iOS tunnel")
                } else {
                    _state.value = VpnState.Connected(config.address, currentTimeMillis())
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
        manager.setEnabled(true)
        manager.saveToPreferencesWithCompletionHandler { _ -> }
        return manager
    }
}

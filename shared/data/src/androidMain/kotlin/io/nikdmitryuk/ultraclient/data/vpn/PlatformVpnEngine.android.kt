package io.nikdmitryuk.ultraclient.data.vpn

import android.content.Context
import android.content.Intent
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

actual class PlatformVpnEngine(private val context: Context) : VpnEngine {

    actual override val state: Flow<VpnState>
        get() = VpnStateHolder.state

    actual override suspend fun connect(
        config: VlessConfig,
        antiDetect: AntiDetectConfig
    ): Result<Unit> = runCatching {
        VpnStateHolder.emit(VpnState.Connecting)
        val intent = Intent(context, Class.forName("io.nikdmitryuk.ultraclient.android.vpn.UltraVpnService"))
        intent.action = "io.nikdmitryuk.ultraclient.ACTION_CONNECT"
        intent.putExtra("vless_config_json", Json.encodeToString(VlessConfig.serializer(), config))
        intent.putExtra("anti_detect_json", Json.encodeToString(AntiDetectConfig.serializer(), antiDetect))
        context.startService(intent)
    }

    actual override suspend fun disconnect(): Result<Unit> = runCatching {
        val intent = Intent(context, Class.forName("io.nikdmitryuk.ultraclient.android.vpn.UltraVpnService"))
        intent.action = "io.nikdmitryuk.ultraclient.ACTION_DISCONNECT"
        context.startService(intent)
    }

    actual override fun isConnected(): Boolean =
        VpnStateHolder.state.value is VpnState.Connected
}

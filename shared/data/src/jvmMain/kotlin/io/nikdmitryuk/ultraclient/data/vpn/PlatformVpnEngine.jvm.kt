package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class PlatformVpnEngine : io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine {
    actual override val state: Flow<VpnState> = emptyFlow()

    actual override suspend fun connect(
        config: VlessConfig,
        antiDetect: AntiDetectConfig,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("JVM target is for testing only"))

    actual override suspend fun disconnect(): Result<Unit> = Result.failure(UnsupportedOperationException("JVM target is for testing only"))

    actual override fun isConnected(): Boolean = false
}

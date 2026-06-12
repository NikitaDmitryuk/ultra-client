package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

actual class PlatformVpnEngine : io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine {
    private val stateFlow = MutableStateFlow<VpnState>(VpnState.Disconnected)
    actual override val state: Flow<VpnState> = stateFlow
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var configFile: File? = null

    actual override suspend fun connect(
        config: VlessConfig,
        antiDetect: AntiDetectConfig,
    ): Result<Unit> =
        runCatching {
            disconnect()
            stateFlow.value = VpnState.Connecting
            val appDir =
                File(System.getProperty("user.home"), ".ultra-client")
                    .apply { mkdirs() }
            val logsDir = File(appDir, "logs").apply { mkdirs() }
            val runtimeDir = File(appDir, "runtime").apply { mkdirs() }
            val singBoxConfig =
                SingBoxConfigBuilder().build(
                    vlessConfig = config,
                    antiDetect = antiDetect.copy(fakeDnsEnabled = true),
                    options =
                        SingBoxOptions(
                            interfaceName = "ultra0",
                            logLevel = "info",
                            logPath = File(logsDir, "sing-box.log").absolutePath,
                        ),
                )
            val file = File(runtimeDir, "sing-box-config.json").apply { writeText(singBoxConfig) }
            configFile = file
            val binary = resolveSingBoxBinary()
            checkConfig(binary, file, logsDir)
            val started =
                withContext(Dispatchers.IO) {
                    ProcessBuilder(binary.absolutePath, "run", "-c", file.absolutePath)
                        .directory(appDir)
                        .redirectError(ProcessBuilder.Redirect.appendTo(File(logsDir, "sing-box.stderr.log")))
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(File(logsDir, "sing-box.stdout.log")))
                        .start()
                }
            process = started
            delay(750)
            if (!started.isAlive) {
                val error = "sing-box exited during startup. Check ${File(logsDir, "sing-box.stderr.log").absolutePath}"
                stateFlow.value = VpnState.Error(error)
                error(error)
            }
            stateFlow.value = VpnState.Connected(config.address, System.currentTimeMillis())
            startWatchdog(started)
        }

    actual override suspend fun disconnect(): Result<Unit> {
        process?.destroy()
        process = null
        stateFlow.value = VpnState.Disconnected
        return Result.success(Unit)
    }

    actual override fun isConnected(): Boolean = process?.isAlive == true

    private fun startWatchdog(started: Process) {
        scope.launch {
            while (started.isAlive) {
                delay(2_000)
            }
            if (process === started) {
                process = null
                stateFlow.value = VpnState.Error("sing-box process terminated unexpectedly")
            }
        }
    }

    private fun resolveSingBoxBinary(): File {
        val explicit =
            System.getProperty("ultra.singbox.path")
                ?: System.getenv("ULTRA_SING_BOX_PATH")
        if (!explicit.isNullOrBlank()) {
            return File(explicit).takeIf { it.canExecute() }
                ?: error("Configured sing-box binary is not executable: $explicit")
        }
        val bundled =
            File(System.getProperty("user.home"), ".ultra-client/bin/sing-box")
        if (bundled.canExecute()) return bundled
        val pathHit =
            System.getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .map { File(it, "sing-box") }
                .firstOrNull { it.canExecute() }
        return pathHit
            ?: error(
                "sing-box binary/helper is not installed. Set ULTRA_SING_BOX_PATH or install the desktop helper.",
            )
    }

    private suspend fun checkConfig(
        binary: File,
        file: File,
        logsDir: File,
    ) {
        withContext(Dispatchers.IO) {
            val logFile = File(logsDir, "sing-box-check.log")
            val check =
                ProcessBuilder(binary.absolutePath, "check", "-c", file.absolutePath)
                    .redirectError(ProcessBuilder.Redirect.appendTo(logFile))
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                    .start()
            val code = check.waitFor()
            if (code != 0) {
                error("sing-box config check failed. Check ${logFile.absolutePath}")
            }
        }
    }
}

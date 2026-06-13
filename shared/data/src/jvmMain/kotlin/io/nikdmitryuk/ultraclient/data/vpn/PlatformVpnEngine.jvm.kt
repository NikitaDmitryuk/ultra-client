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
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

actual class PlatformVpnEngine : io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine {
    private val stateFlow = MutableStateFlow<VpnState>(VpnState.Disconnected)
    actual override val state: Flow<VpnState> = stateFlow
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var privilegedPid: Long? = null
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
            val stdoutLog = File(logsDir, "sing-box.stdout.log").resetLog()
            val stderrLog = File(logsDir, "sing-box.stderr.log").resetLog()
            val singBoxLog = File(logsDir, "sing-box.log").resetLog()
            val checkLog = File(logsDir, "sing-box-check.log").resetLog()
            val runtimeDir = File(appDir, "runtime").apply { mkdirs() }
            val singBoxConfig =
                SingBoxConfigBuilder().build(
                    vlessConfig = config,
                    antiDetect = antiDetect.copy(fakeDnsEnabled = true),
                    options =
                        SingBoxOptions(
                            interfaceName = desktopTunInterfaceName(),
                            logLevel = "info",
                            logPath = singBoxLog.absolutePath,
                            cacheFilePath = File(appDir, "cache.db").absolutePath,
                        ),
                )
            val file = File(runtimeDir, "sing-box-config.json").apply { writeText(singBoxConfig) }
            configFile = file
            val binary = resolveSingBoxBinary()
            checkConfig(binary, file, checkLog)
            if (isMacOs()) {
                val pidFile = File(runtimeDir, "sing-box.pid")
                val response =
                    macOsHelperRequest(
                        command = "start",
                        binary = binary,
                        config = file,
                        stdoutLog = stdoutLog,
                        stderrLog = stderrLog,
                        workingDirectory = appDir,
                    )
                val pid = response.pid
                if (pid != null) {
                    pidFile.writeText(pid.toString())
                    privilegedPid = pid
                }
                delay(1_000)
                if (!macOsHelperStatus().running) {
                    val details = stderrLog.readText().trim().takeIf { it.isNotBlank() }
                    val error =
                        details?.let { "sing-box privileged helper exited during startup: ${it.lineSequence().last()}" }
                            ?: "sing-box privileged helper exited during startup. Check ${stderrLog.absolutePath}"
                    stateFlow.value = VpnState.Error(error)
                    error(error)
            }
            stateFlow.value = VpnState.Connected(config.address, System.currentTimeMillis())
            startPrivilegedWatchdog()
            return@runCatching
        }

            val started =
                withContext(Dispatchers.IO) {
                    ProcessBuilder(binary.absolutePath, "run", "-c", file.absolutePath)
                        .directory(appDir)
                        .redirectError(ProcessBuilder.Redirect.appendTo(stderrLog))
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutLog))
                        .start()
                }
            process = started
            delay(750)
            if (!started.isAlive) {
                val details = stderrLog.readText().trim().takeIf { it.isNotBlank() }
                val error =
                    when {
                        details?.contains("operation not permitted", ignoreCase = true) == true ->
                            "Desktop VPN requires a privileged helper/root permissions to create the TUN interface."
                        details != null ->
                            "sing-box exited during startup: ${details.lineSequence().last()}"
                        else ->
                            "sing-box exited during startup. Check ${stderrLog.absolutePath}"
                    }
                stateFlow.value = VpnState.Error(error)
                error(error)
            }
            stateFlow.value = VpnState.Connected(config.address, System.currentTimeMillis())
            startWatchdog(started)
        }.onFailure { error ->
            process?.destroy()
            process = null
            privilegedPid = null
            stateFlow.value = VpnState.Error(error.message ?: "Failed to start desktop VPN")
        }

    actual override suspend fun disconnect(): Result<Unit> {
        process?.destroy()
        process = null
        if (isMacOs()) {
            runCatching { macOsHelperRequest(command = "stop") }
            macOsPidFile().delete()
        }
        privilegedPid = null
        stateFlow.value = VpnState.Disconnected
        return Result.success(Unit)
    }

    actual override fun isConnected(): Boolean =
        process?.isAlive == true || (isMacOs() && runCatching { macOsHelperStatus().running }.getOrDefault(false))

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

    private fun startPrivilegedWatchdog() {
        scope.launch {
            while (runCatching { macOsHelperStatus().running }.getOrDefault(false)) {
                delay(2_000)
            }
            if (privilegedPid != null) {
                privilegedPid = null
                stateFlow.value = VpnState.Error("sing-box privileged helper terminated unexpectedly")
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
        devSingBoxCandidates().firstOrNull { it.canExecute() }?.let { return it }
        val pathHit =
            System.getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .map { File(it, "sing-box") }
                .firstOrNull { it.canExecute() }
        return pathHit
            ?: error(
                "sing-box binary/helper is not installed. Set ULTRA_SING_BOX_PATH, run `make sing-box-desktop`, or install the desktop helper.",
            )
    }

    private fun devSingBoxCandidates(): Sequence<File> =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .flatMap { dir ->
                sequenceOf(
                    File(dir, "sing-box-build/output/desktop/sing-box"),
                    File(dir, "ultra-client/sing-box-build/output/desktop/sing-box"),
                )
            }

    private fun desktopTunInterfaceName(): String {
        return when {
            isMacOs() -> ""
            isWindows() -> "Ultra"
            else -> "ultra0"
        }
    }

    private fun isMacOs(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        return "mac" in osName || "darwin" in osName
    }

    private fun isWindows(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        return "win" in osName
    }

    private suspend fun macOsHelperRequest(
        command: String,
        binary: File? = null,
        config: File? = null,
        stdoutLog: File? = null,
        stderrLog: File? = null,
        workingDirectory: File? = null,
    ): HelperResponse =
        withContext(Dispatchers.IO) {
            val socket = File(MACOS_HELPER_SOCKET)
            if (!socket.exists()) {
                error("macOS VPN helper is not installed. Run `make macos-helper-install` and try again.")
            }
            val body =
                buildString {
                    append("{\"command\":")
                    append(command.jsonString())
                    binary?.let { append(",\"singBox\":").append(it.absolutePath.jsonString()) }
                    config?.let { append(",\"config\":").append(it.absolutePath.jsonString()) }
                    stdoutLog?.let { append(",\"stdout\":").append(it.absolutePath.jsonString()) }
                    stderrLog?.let { append(",\"stderr\":").append(it.absolutePath.jsonString()) }
                    workingDirectory?.let { append(",\"workingDirectory\":").append(it.absolutePath.jsonString()) }
                    append("}")
                }
            val address = UnixDomainSocketAddress.of(MACOS_HELPER_SOCKET)
            SocketChannel.open(address).use { channel ->
                channel.write(ByteBuffer.wrap(body.toByteArray(StandardCharsets.UTF_8)))
                channel.shutdownOutput()
                val buffer = ByteBuffer.allocate(16 * 1024)
                val bytes = mutableListOf<Byte>()
                while (channel.read(buffer) > 0) {
                    buffer.flip()
                    while (buffer.hasRemaining()) bytes += buffer.get()
                    buffer.clear()
                }
                val response = bytes.toByteArray().toString(StandardCharsets.UTF_8).trim()
                val parsed = parseHelperResponse(response)
                if (!parsed.ok) {
                    error(parsed.message ?: "macOS VPN helper request failed")
                }
                parsed
            }
        }

    private fun macOsHelperStatus(): HelperStatus {
        val response =
            runBlockingHelperRequest(
                body = "{\"command\":\"status\"}",
            )
        val parsed = parseHelperResponse(response)
        if (!parsed.ok) error(parsed.message ?: "macOS VPN helper status failed")
        return HelperStatus(running = parsed.message == "running", pid = parsed.pid)
    }

    private fun runBlockingHelperRequest(body: String): String {
        val socket = File(MACOS_HELPER_SOCKET)
        if (!socket.exists()) return "{\"ok\":true,\"message\":\"stopped\"}"
        val address = UnixDomainSocketAddress.of(MACOS_HELPER_SOCKET)
        return SocketChannel.open(address).use { channel ->
            channel.write(ByteBuffer.wrap(body.toByteArray(StandardCharsets.UTF_8)))
            channel.shutdownOutput()
            val buffer = ByteBuffer.allocate(16 * 1024)
            val bytes = mutableListOf<Byte>()
            while (channel.read(buffer) > 0) {
                buffer.flip()
                while (buffer.hasRemaining()) bytes += buffer.get()
                buffer.clear()
            }
            bytes.toByteArray().toString(StandardCharsets.UTF_8).trim()
        }
    }

    private fun macOsPidFile(): File =
        File(System.getProperty("user.home"), ".ultra-client/runtime/sing-box.pid")

    private fun parseHelperResponse(response: String): HelperResponse {
        val ok = response.contains("\"ok\":true")
        val message =
            Regex(""""message"\s*:\s*"((?:\\.|[^"])*)"""")
                .find(response)
                ?.groupValues
                ?.get(1)
                ?.jsonUnescape()
        val pid =
            Regex(""""pid"\s*:\s*(\d+)""")
                .find(response)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
        return HelperResponse(ok = ok, message = message, pid = pid)
    }

    private suspend fun checkConfig(
        binary: File,
        file: File,
        logFile: File,
    ) {
        withContext(Dispatchers.IO) {
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

    private fun File.resetLog(): File = apply {
        parentFile?.mkdirs()
        if (exists() && !canWrite()) {
            delete()
        }
        writeText("")
    }

    private fun String.jsonString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun String.jsonUnescape(): String =
        replace("\\\"", "\"").replace("\\\\", "\\")

    private data class HelperResponse(
        val ok: Boolean,
        val message: String?,
        val pid: Long?,
    )

    private data class HelperStatus(
        val running: Boolean,
        val pid: Long?,
    )

    private companion object {
        private const val MACOS_HELPER_SOCKET = "/var/run/ultra-client-helper.sock"
    }
}

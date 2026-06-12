package io.nikdmitryuk.ultraclient.android.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import java.io.File
import java.lang.reflect.Proxy

object SingBoxBridge {
    private const val TAG = "SingBoxBridge"

    private val libboxClass: Class<*>? by lazy {
        listOf(
            "io.nekohasekai.libbox.Libbox",
            "io.nekohasekai.libbox.LibBox",
            "libbox.Libbox",
            "libbox.LibBox",
        ).firstNotNullOfOrNull { name ->
            try {
                Class.forName(name)
            } catch (_: ClassNotFoundException) {
                null
            }
        }
    }

    private var commandServer: Any? = null
    private var tunFd: ParcelFileDescriptor? = null

    @Synchronized
    fun start(
        configJson: String,
        antiDetect: AntiDetectConfig,
        cacheDir: File,
        vpn: VpnService,
    ): String? {
        val libbox = libboxClass ?: return "sing-box mobile binding not found (SingBoxCore.aar missing)"
        stop()
        return runCatching {
            setup(libbox, cacheDir)
            val handler = newCommandServerHandler(libbox)
            val platform = newPlatformInterface(libbox, vpn, antiDetect)
            val server =
                libbox.invokeStaticAny(listOf("newCommandServer", "NewCommandServer"), handler, platform)
                    ?: error("libbox.NewCommandServer returned null")
            server.invokeAny(listOf("start", "Start"))
            val overrideOptions = newOverrideOptions(libbox, antiDetect)
            server.invokeAny(listOf("startOrReloadService", "StartOrReloadService"), configJson, overrideOptions)
            commandServer = server
            Log.i(TAG, "sing-box StartOrReloadService OK configChars=${configJson.length}")
            null
        }.getOrElse { t ->
            stop()
            Log.e(TAG, "sing-box start failed", t)
            t.message ?: t.javaClass.simpleName
        }
    }

    @Synchronized
    fun stop(): Boolean {
        val server =
            commandServer ?: run {
                closeTun()
                return false
            }
        commandServer = null
        val closeService = runCatching { server.invokeAny(listOf("closeService", "CloseService")) }.isSuccess
        val closeServer = runCatching { server.invokeAny(listOf("close", "Close")) }.isSuccess
        closeTun()
        return closeService || closeServer
    }

    fun isRunning(): Boolean = commandServer != null

    private fun setup(
        libbox: Class<*>,
        cacheDir: File,
    ) {
        val setupClass = libbox.loadSibling("SetupOptions")
        val setupOptions = setupClass.getDeclaredConstructor().newInstance()
        val basePath = File(cacheDir, "sing-box").apply { mkdirs() }
        val workPath = File(basePath, "work").apply { mkdirs() }
        val tempPath = File(basePath, "tmp").apply { mkdirs() }
        setupOptions.setValue("basePath", "BasePath", basePath.absolutePath)
        setupOptions.setValue("workingPath", "WorkingPath", workPath.absolutePath)
        setupOptions.setValue("tempPath", "TempPath", tempPath.absolutePath)
        setupOptions.setValue("fixAndroidStack", "FixAndroidStack", true)
        setupOptions.setValue("commandServerListenPort", "CommandServerListenPort", 0)
        setupOptions.setValue("commandServerSecret", "CommandServerSecret", "")
        setupOptions.setValue("logMaxLines", "LogMaxLines", 300L)
        setupOptions.setValue("debug", "Debug", false)
        libbox.invokeStaticAny(listOf("setup", "Setup"), setupOptions)
    }

    private fun newCommandServerHandler(libbox: Class<*>): Any {
        val handlerClass = libbox.loadSibling("CommandServerHandler")
        return Proxy.newProxyInstance(handlerClass.classLoader, arrayOf(handlerClass)) { _, method, args ->
            when (method.name) {
                "serviceStop", "ServiceStop", "serviceReload", "ServiceReload" -> null
                "getSystemProxyStatus", "GetSystemProxyStatus" -> null
                "setSystemProxyEnabled", "SetSystemProxyEnabled" -> null
                "writeDebugMessage", "WriteDebugMessage" -> {
                    Log.d(TAG, args?.firstOrNull() as? String ?: "")
                    null
                }
                else -> defaultReturn(method.returnType)
            }
        }
    }

    private fun newPlatformInterface(
        libbox: Class<*>,
        vpn: VpnService,
        antiDetect: AntiDetectConfig,
    ): Any {
        val platformClass = libbox.loadSibling("PlatformInterface")
        return Proxy.newProxyInstance(platformClass.classLoader, arrayOf(platformClass)) { _, method, args ->
            when (method.name) {
                "localDNSTransport", "LocalDNSTransport" -> null
                "usePlatformAutoDetectInterfaceControl", "UsePlatformAutoDetectInterfaceControl" -> true
                "autoDetectInterfaceControl", "AutoDetectInterfaceControl" -> {
                    val fd = (args?.firstOrNull() as? Number)?.toInt() ?: return@newProxyInstance null
                    vpn.protect(fd)
                    null
                }
                "openTun", "OpenTun" -> {
                    val options = args?.firstOrNull() ?: error("OpenTun called without options")
                    val tun = TunConfigurator(vpn).establishForSingBox(options, antiDetect)
                    tunFd = tun
                    tun.fd
                }
                "useProcFS", "UseProcFS" -> true
                "findConnectionOwner", "FindConnectionOwner" -> null
                "startDefaultInterfaceMonitor", "StartDefaultInterfaceMonitor" -> null
                "closeDefaultInterfaceMonitor", "CloseDefaultInterfaceMonitor" -> null
                "getInterfaces", "GetInterfaces" -> emptyIterator(libbox, "NetworkInterfaceIterator", null)
                "underNetworkExtension", "UnderNetworkExtension" -> false
                "includeAllNetworks", "IncludeAllNetworks" -> false
                "readWIFIState", "ReadWIFIState" -> null
                "systemCertificates", "SystemCertificates" -> emptyIterator(libbox, "StringIterator", "")
                "clearDNSCache", "ClearDNSCache" -> null
                "sendNotification", "SendNotification" -> null
                else -> defaultReturn(method.returnType)
            }
        }
    }

    private fun newOverrideOptions(
        libbox: Class<*>,
        antiDetect: AntiDetectConfig,
    ): Any {
        val optionsClass = libbox.loadSibling("OverrideOptions")
        val options = optionsClass.getDeclaredConstructor().newInstance()
        val includePackages = antiDetect.vpnIncludedApps.filter { it.throughVpn }.map { it.appId }
        val excludePackages = if (includePackages.isEmpty()) antiDetect.legacyBypassAppIds else emptyList()
        options.setValue("autoRedirect", "AutoRedirect", false)
        options.setValue("includePackage", "IncludePackage", stringIterator(libbox, includePackages))
        options.setValue("excludePackage", "ExcludePackage", stringIterator(libbox, excludePackages))
        return options
    }

    private fun stringIterator(
        libbox: Class<*>,
        values: List<String>,
    ): Any {
        val iteratorClass = libbox.loadSibling("StringIterator")
        var index = 0
        return Proxy.newProxyInstance(iteratorClass.classLoader, arrayOf(iteratorClass)) { _, method, _ ->
            when (method.name) {
                "len", "Len" -> values.size
                "hasNext", "HasNext" -> index < values.size
                "next", "Next" -> values.getOrElse(index++) { "" }
                else -> defaultReturn(method.returnType)
            }
        }
    }

    private fun emptyIterator(
        libbox: Class<*>,
        siblingName: String,
        emptyValue: Any?,
    ): Any {
        val iteratorClass = libbox.loadSibling(siblingName)
        return Proxy.newProxyInstance(iteratorClass.classLoader, arrayOf(iteratorClass)) { _, method, _ ->
            when (method.name) {
                "len", "Len" -> 0
                "hasNext", "HasNext" -> false
                "next", "Next" -> emptyValue
                else -> defaultReturn(method.returnType)
            }
        }
    }

    private fun closeTun() {
        runCatching { tunFd?.close() }
        tunFd = null
    }

    private fun defaultReturn(type: Class<*>): Any? =
        when (type) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            Void.TYPE -> null
            else -> null
        }

    private fun Class<*>.loadSibling(simpleName: String): Class<*> = Class.forName("$packageName.$simpleName", true, classLoader)

    private fun Class<*>.invokeStaticAny(
        names: List<String>,
        vararg args: Any?,
    ): Any? =
        methods
            .firstOrNull { method -> names.any { it == method.name } && method.parameterTypes.size == args.size }
            ?.invoke(null, *args)
            ?: error("Missing static method ${names.joinToString("|")} on $name")

    private fun Any.invokeAny(
        names: List<String>,
        vararg args: Any?,
    ): Any? =
        javaClass.methods
            .firstOrNull { method -> names.any { it == method.name } && method.parameterTypes.size == args.size }
            ?.invoke(this, *args)
            ?: error("Missing method ${names.joinToString("|")} on ${javaClass.name}")

    private fun Any.setValue(
        lowerName: String,
        upperName: String,
        value: Any?,
    ) {
        javaClass.methods
            .firstOrNull { it.name == "set$upperName" && it.parameterTypes.size == 1 }
            ?.invoke(this, value)
            ?: javaClass.fields
                .firstOrNull { it.name == lowerName || it.name == upperName }
                ?.set(this, value)
    }
}

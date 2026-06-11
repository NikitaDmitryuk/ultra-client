package io.nikdmitryuk.ultraclient.android.vpn

import android.net.VpnService
import android.util.Base64
import android.util.Log
import io.nikdmitryuk.ultraclient.android.BuildConfig
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

object XrayBridge {
    private const val TAG = "XrayBridge"
    private val dialerCallbackCount = AtomicInteger(0)
    private val libxrayClass: Class<*>? by lazy {
        try {
            Class.forName("libXray.LibXray")
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    /** Returns null on success, or an error message string on failure. */
    fun startXray(
        configJson: String,
        tunFd: Int,
        datDir: String,
        vpn: VpnService,
    ): String? {
        val lib = libxrayClass ?: return "XrayCore library not found (AAR missing)"
        return try {
            registerLibXraySocketProtection(vpn, lib)

            lib.getMethod("setTunFd", Int::class.java).invoke(null, tunFd)

            val base64Request =
                lib
                    .getMethod(
                        "newXrayRunFromJSONRequest",
                        String::class.java,
                        String::class.java,
                        String::class.java,
                    ).invoke(null, datDir, "", configJson) as? String ?: ""

            val base64Response =
                lib
                    .getMethod("runXrayFromJSON", String::class.java)
                    .invoke(null, base64Request) as? String ?: ""

            val err = decodeError(base64Response)
            if (err == null) {
                Log.i(TAG, "runXrayFromJSON OK configChars=${configJson.length}")
            } else {
                Log.e(
                    TAG,
                    "runXrayFromJSON error=$err responseChars=${base64Response.length}",
                )
            }
            err
        } catch (e: InvocationTargetException) {
            val cause = e.cause ?: e
            Log.e(TAG, "startXray failed", cause)
            cause.message ?: cause.javaClass.simpleName
        } catch (e: Exception) {
            Log.e(TAG, "startXray failed", e)
            e.message ?: e.javaClass.simpleName
        }
    }

    /**
     * libXray ожидает [android_wrapper.RegisterDialerController]: иначе исходящий TCP к VLESS
     * идёт снова в TUN (петля). Speedtest в логах: `from /10.0.0.1` + таймаут — типичный симптом.
     */
    private fun registerLibXraySocketProtection(
        vpn: VpnService,
        lib: Class<*>,
    ) {
        dialerCallbackCount.set(0)
        val iface =
            findDialerControllerInterface(lib) ?: run {
                Log.e(
                    TAG,
                    "DialerController interface NOT FOUND — socket protect skipped. " +
                        "Check AAR: list LibXray inner classes / package name.",
                )
                logLibXrayDialerRelatedMethods(lib)
                return
            }
        Log.i(TAG, "Using DialerController iface=${iface.name}")
        val proxy =
            Proxy.newProxyInstance(
                iface.classLoader,
                arrayOf(iface),
            ) { _, method, args ->
                val n = dialerCallbackCount.incrementAndGet()
                if (BuildConfig.DEBUG && n <= 8) {
                    Log.d(
                        TAG,
                        "DialerCallback #$n ${method.name}(${args?.joinToString()}) retType=${method.returnType.simpleName}",
                    )
                }
                if (!method.name.equals("protectFd", ignoreCase = true)) {
                    Log.w(TAG, "DialerCallback unexpected method (ignored as false): ${method.name}")
                    return@newProxyInstance false
                }
                val raw = args?.getOrNull(0) ?: return@newProxyInstance false
                val fd: Int =
                    when (raw) {
                        is Int -> raw
                        is Long -> raw.toInt()
                        is Number -> raw.toInt()
                        else -> return@newProxyInstance false
                    }
                val ok = vpn.protect(fd)
                if (BuildConfig.DEBUG && n <= 6) {
                    Log.d(TAG, "vpn.protect(fd=$fd) -> $ok")
                }
                ok
            }
        try {
            lib.getMethod("registerDialerController", iface).invoke(null, proxy)
            Log.i(TAG, "registerDialerController OK")
        } catch (e: Exception) {
            Log.e(TAG, "registerDialerController FAILED", e)
        }
        try {
            lib.getMethod("registerListenerController", iface).invoke(null, proxy)
            Log.i(TAG, "registerListenerController OK")
        } catch (e: Exception) {
            Log.d(TAG, "registerListenerController skipped or failed: ${e.message}")
        }
        if (BuildConfig.LIBXRAY_SKIP_INIT_DNS) {
            Log.w(TAG, "initDns skipped (LIBXRAY_SKIP_INIT_DNS=true)")
        } else {
            try {
                lib.getMethod("initDns", iface, String::class.java).invoke(null, proxy, "1.1.1.1:53")
                Log.i(TAG, "initDns(1.1.1.1:53) OK")
            } catch (_: Exception) {
                try {
                    lib.getMethod("InitDns", iface, String::class.java).invoke(null, proxy, "1.1.1.1:53")
                    Log.i(TAG, "InitDns OK")
                } catch (e: Exception) {
                    Log.w(TAG, "initDns not available: ${e.message}")
                }
            }
        }
    }

    private fun logLibXrayDialerRelatedMethods(lib: Class<*>) {
        try {
            for (m in lib.declaredMethods) {
                val name = m.name
                if (name.contains("register", ignoreCase = true) ||
                    name.contains("Dialer", ignoreCase = true) ||
                    name.contains("Dns", ignoreCase = true) ||
                    name.contains("Protect", ignoreCase = true)
                ) {
                    Log.d(
                        TAG,
                        "LibXray.$name(${m.parameterTypes.joinToString { it.simpleName }})",
                    )
                }
            }
            for (c in lib.declaredClasses) {
                if (c.simpleName.contains("Dialer", ignoreCase = true)) {
                    Log.d(TAG, "LibXray nested: ${c.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "logLibXrayDialerRelatedMethods failed: ${e.message}")
        }
    }

    private fun findDialerControllerInterface(lib: Class<*>): Class<*>? {
        val names =
            listOf(
                "libXray.DialerController",
                "libXray.LibXray\$DialerController",
                "libxray.DialerController",
                "libxray.Libxray\$DialerController",
                "io.xtls.libxray.DialerController",
                "io.xtls.libxray.Libxray\$DialerController",
            )
        for (n in names) {
            try {
                return Class.forName(n)
            } catch (_: ClassNotFoundException) {
                continue
            }
        }
        try {
            for (c in lib.declaredClasses) {
                if (!c.isInterface) continue
                if (!Modifier.isPublic(c.modifiers)) continue
                if (c.methods.any { it.name.equals("protectFd", ignoreCase = true) }) {
                    Log.i(TAG, "Resolved DialerController via LibXray.declaredClasses: ${c.name}")
                    return c
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scan LibXray.declaredClasses failed", e)
        }
        return null
    }

    fun stopXray(): Boolean {
        val lib = libxrayClass ?: return false
        return try {
            val base64Response = lib.getMethod("stopXray").invoke(null) as? String ?: ""
            val ok = decodeError(base64Response) == null
            try {
                lib.getMethod("resetDns").invoke(null)
            } catch (_: Exception) {
                try {
                    lib.getMethod("ResetDns").invoke(null)
                } catch (_: Exception) {
                }
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "stopXray failed", e)
            false
        }
    }

    fun isRunning(): Boolean {
        val lib = libxrayClass ?: return false
        return try {
            lib.getMethod("getXrayState").invoke(null) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ожидание, пока [getXrayState] станет true (поллинг). Не завершать успехом при первой синхронной проверке после [runXrayFromJSON].
     */
    suspend fun awaitCoreReady(
        pollIntervalMs: Long = 250L,
        timeoutMs: Long = 5_000L,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isRunning()) return true
            delay(pollIntervalMs)
        }
        return isRunning()
    }

    /**
     * Обёртка над libXray.Ping (gomobile: `Ping(String base64)`).
     * Ожидается JSON [datDir], [configPath], timeout, url, proxy — см. upstream libXray `pingRequest`.
     *
     * Не вызывать параллельно с уже запущенным [runXrayFromJSON]: в upstream Ping поднимает отдельный экземпляр для замера.
     * Удобно для офлайн-проверки конфига или скриптов; цепочка readiness после TUN опирается на [awaitCoreReady].
     */
    fun pingOutboundConfigPath(
        datDir: String,
        configPath: String,
        timeoutSec: Int = 8,
        testUrl: String = "https://www.google.com/generate_204",
        proxy: String = "",
    ): String? {
        val lib = libxrayClass ?: return "XrayCore library not found"
        val ping =
            try {
                lib.getMethod("Ping", String::class.java)
            } catch (_: NoSuchMethodException) {
                try {
                    lib.getMethod("ping", String::class.java)
                } catch (_: NoSuchMethodException) {
                    return "LibXray.Ping not available in this AAR"
                }
            }
        val req =
            JSONObject().apply {
                put("datDir", datDir)
                put("configPath", configPath)
                put("timeout", timeoutSec)
                put("url", testUrl)
                put("proxy", proxy)
            }
        val base64Req =
            Base64.encodeToString(
                req.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
        return try {
            val base64Response = ping.invoke(null, base64Req) as? String ?: return "empty Ping response"
            decodeError(base64Response)
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    /**
     * libXray.TestXray(base64) — проверка конфига по файлу (upstream `RunXrayRequest`). Не параллельно с работающим ядром.
     */
    fun testXrayConfigPath(
        datDir: String,
        configPath: String,
        mphCachePath: String = "",
        tunFd: Int? = null,
    ): String? {
        val lib = libxrayClass ?: return "XrayCore library not found"
        val test =
            try {
                lib.getMethod("TestXray", String::class.java)
            } catch (_: NoSuchMethodException) {
                try {
                    lib.getMethod("testXray", String::class.java)
                } catch (_: NoSuchMethodException) {
                    return "LibXray.TestXray not available in this AAR"
                }
            }
        val req =
            JSONObject().apply {
                put("datDir", datDir)
                put("configPath", configPath)
                put("mphCachePath", mphCachePath)
            }
        val base64Req =
            Base64.encodeToString(
                req.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
        return try {
            if (tunFd != null) {
                lib.getMethod("setTunFd", Int::class.java).invoke(null, tunFd)
            }
            val base64Response = test.invoke(null, base64Req) as? String ?: return "empty TestXray response"
            decodeError(base64Response)
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    fun queryStats(serverAddr: String): String {
        val lib = libxrayClass ?: return "{}"
        return try {
            val base64Req = Base64.encodeToString(serverAddr.toByteArray(), Base64.NO_WRAP)
            val base64Response =
                lib
                    .getMethod("queryStats", String::class.java)
                    .invoke(null, base64Req) as? String ?: ""
            val json = JSONObject(String(Base64.decode(base64Response, Base64.DEFAULT)))
            json.optString("data", "{}")
        } catch (_: Exception) {
            "{}"
        }
    }

    private fun decodeError(base64Response: String): String? =
        try {
            val json = JSONObject(String(Base64.decode(base64Response, Base64.DEFAULT)))
            json.optString("error", "").ifEmpty { null }
        } catch (_: Exception) {
            null
        }
}

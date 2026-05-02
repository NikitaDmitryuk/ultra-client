package io.nikdmitryuk.ultraclient.android.vpn

import android.util.Base64
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException

object XrayBridge {
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
    ): String? {
        val lib = libxrayClass ?: return "XrayCore library not found (AAR missing)"
        return try {
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

            decodeError(base64Response)
        } catch (e: InvocationTargetException) {
            val cause = e.cause ?: e
            android.util.Log.e("XrayBridge", "startXray failed", cause)
            cause.message ?: cause.javaClass.simpleName
        } catch (e: Exception) {
            android.util.Log.e("XrayBridge", "startXray failed", e)
            e.message ?: e.javaClass.simpleName
        }
    }

    fun stopXray(): Boolean {
        val lib = libxrayClass ?: return false
        return try {
            val base64Response = lib.getMethod("stopXray").invoke(null) as? String ?: ""
            decodeError(base64Response) == null
        } catch (e: Exception) {
            android.util.Log.e("XrayBridge", "stopXray failed", e)
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

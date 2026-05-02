package io.nikdmitryuk.ultraclient.android.vpn

object XrayBridge {
    fun startXray(
        configJson: String,
        tunFd: Int,
    ): Boolean =
        try {
            // libxray.Libxray.startXray(configJson, tunFd.toLong())
            // The exact method depends on the compiled XrayCore.aar.
            // Reflection-based call allows compile without the AAR during development.
            val libxray = Class.forName("libxray.Libxray")
            val method = libxray.getMethod("startXray", String::class.java, Long::class.java)
            val result = method.invoke(null, configJson, tunFd.toLong()) as? String ?: ""
            result.isEmpty()
        } catch (e: ClassNotFoundException) {
            // XrayCore AAR not yet linked — log and return false
            android.util.Log.w("XrayBridge", "XrayCore not available: ${e.message}")
            false
        } catch (e: Exception) {
            android.util.Log.e("XrayBridge", "startXray failed", e)
            false
        }

    fun stopXray(): Boolean =
        try {
            val libxray = Class.forName("libxray.Libxray")
            val method = libxray.getMethod("stopXray")
            val result = method.invoke(null) as? String ?: ""
            result.isEmpty()
        } catch (e: Exception) {
            android.util.Log.e("XrayBridge", "stopXray failed", e)
            false
        }

    fun isRunning(): Boolean =
        try {
            val libxray = Class.forName("libxray.Libxray")
            val method = libxray.getMethod("isXrayRunning")
            method.invoke(null) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }

    fun queryStats(
        tag: String,
        direct: String,
    ): String =
        try {
            val libxray = Class.forName("libxray.Libxray")
            val method = libxray.getMethod("queryStats", String::class.java, String::class.java)
            method.invoke(null, tag, direct) as? String ?: "{}"
        } catch (e: Exception) {
            "{}"
        }
}

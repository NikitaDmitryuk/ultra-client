package io.nikdmitryuk.ultraclient.android.vpn

import android.content.Context
import java.io.File

object GeoDataInstaller {
    private val files = listOf("geoip.dat", "geosite.dat")

    fun ensureInstalled(context: Context): String {
        val dir = context.filesDir
        for (name in files) {
            val dest = File(dir, name)
            if (!dest.exists()) {
                context.assets.open(name).use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
        }
        return dir.absolutePath
    }
}

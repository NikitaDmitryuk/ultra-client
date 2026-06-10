package io.nikdmitryuk.ultraclient.presentation.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.nikdmitryuk.ultraclient.domain.model.VpnAppRouteRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidInstalledAppsProvider(
    private val context: Context,
) : InstalledAppsProvider {
    override suspend fun getInstalledApps(): List<VpnAppRouteRule> =
        withContext(Dispatchers.Default) {
            val pm = context.packageManager
            val launcherIntent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
            val infos =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        launcherIntent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
                }
            val seen = LinkedHashSet<String>()
            val out = ArrayList<VpnAppRouteRule>(infos.size)
            for (info in infos) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (!seen.add(pkg)) continue
                val label =
                    try {
                        info.loadLabel(pm).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                out.add(
                    VpnAppRouteRule(
                        appId = pkg,
                        appName = label,
                        throughVpn = false,
                    ),
                )
            }
            out.sortBy { it.appName.lowercase() }
            out
        }
}

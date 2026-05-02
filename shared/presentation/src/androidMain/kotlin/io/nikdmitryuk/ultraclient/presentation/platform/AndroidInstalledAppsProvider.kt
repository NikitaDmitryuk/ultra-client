package io.nikdmitryuk.ultraclient.presentation.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.nikdmitryuk.ultraclient.domain.model.SplitTunnelRule

class AndroidInstalledAppsProvider(private val context: Context) : InstalledAppsProvider {
    override suspend fun getInstalledApps(): List<SplitTunnelRule> =
        context.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map {
                SplitTunnelRule(
                    appId = it.packageName,
                    appName = it.loadLabel(context.packageManager).toString(),
                    isExcluded = false
                )
            }
            .sortedBy { it.appName }
}

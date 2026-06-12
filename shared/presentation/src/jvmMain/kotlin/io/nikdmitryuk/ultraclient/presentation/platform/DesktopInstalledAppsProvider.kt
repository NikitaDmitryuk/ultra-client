package io.nikdmitryuk.ultraclient.presentation.platform

import io.nikdmitryuk.ultraclient.domain.model.VpnAppRouteRule

class DesktopInstalledAppsProvider : InstalledAppsProvider {
    override suspend fun getInstalledApps(): List<VpnAppRouteRule> = emptyList()
}

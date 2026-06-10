package io.nikdmitryuk.ultraclient.presentation.platform

import io.nikdmitryuk.ultraclient.domain.model.VpnAppRouteRule

class IosInstalledAppsProvider : InstalledAppsProvider {
    override suspend fun getInstalledApps(): List<VpnAppRouteRule> = emptyList()
}

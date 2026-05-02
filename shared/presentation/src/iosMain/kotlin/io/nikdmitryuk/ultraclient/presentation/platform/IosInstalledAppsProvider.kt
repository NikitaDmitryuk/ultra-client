package io.nikdmitryuk.ultraclient.presentation.platform

import io.nikdmitryuk.ultraclient.domain.model.SplitTunnelRule

class IosInstalledAppsProvider : InstalledAppsProvider {
    override suspend fun getInstalledApps(): List<SplitTunnelRule> = emptyList()
}

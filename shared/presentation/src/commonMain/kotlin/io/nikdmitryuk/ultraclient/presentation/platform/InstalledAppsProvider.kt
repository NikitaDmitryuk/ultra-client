package io.nikdmitryuk.ultraclient.presentation.platform

import io.nikdmitryuk.ultraclient.domain.model.SplitTunnelRule

interface InstalledAppsProvider {
    suspend fun getInstalledApps(): List<SplitTunnelRule>
}

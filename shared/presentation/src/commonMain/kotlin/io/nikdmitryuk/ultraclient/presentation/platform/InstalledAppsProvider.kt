package io.nikdmitryuk.ultraclient.presentation.platform

import io.nikdmitryuk.ultraclient.domain.model.VpnAppRouteRule

interface InstalledAppsProvider {
    suspend fun getInstalledApps(): List<VpnAppRouteRule>
}

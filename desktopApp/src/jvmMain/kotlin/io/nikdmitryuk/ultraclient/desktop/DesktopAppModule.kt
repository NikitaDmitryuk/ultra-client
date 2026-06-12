package io.nikdmitryuk.ultraclient.desktop

import io.nikdmitryuk.ultraclient.domain.usecase.ConnectVpnUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.DeleteProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.DisconnectVpnUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.GetProfilesUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.GetVpnStateUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.ImportProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.SetActiveProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.UpdateAntiDetectUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.UpdateVpnIncludedAppsUseCase
import io.nikdmitryuk.ultraclient.presentation.platform.ClipboardReader
import io.nikdmitryuk.ultraclient.presentation.platform.DesktopClipboardReader
import io.nikdmitryuk.ultraclient.presentation.platform.DesktopInstalledAppsProvider
import io.nikdmitryuk.ultraclient.presentation.platform.InstalledAppsProvider
import org.koin.dsl.module

val desktopAppModule =
    module {
        single<ClipboardReader> { DesktopClipboardReader() }
        single<InstalledAppsProvider> { DesktopInstalledAppsProvider() }

        factory { ConnectVpnUseCase(get(), get(), get()) }
        factory { DisconnectVpnUseCase(get()) }
        factory { ImportProfileUseCase(get(), get()) }
        factory { GetVpnStateUseCase(get()) }
        factory { GetProfilesUseCase(get()) }
        factory { DeleteProfileUseCase(get()) }
        factory { SetActiveProfileUseCase(get()) }
        factory { UpdateVpnIncludedAppsUseCase(get()) }
        factory { UpdateAntiDetectUseCase(get()) }
    }

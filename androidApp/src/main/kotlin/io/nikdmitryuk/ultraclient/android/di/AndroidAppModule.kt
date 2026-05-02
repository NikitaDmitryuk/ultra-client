package io.nikdmitryuk.ultraclient.android.di

import io.nikdmitryuk.ultraclient.domain.usecase.ConnectVpnUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.DeleteProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.DisconnectVpnUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.GetProfilesUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.GetVpnStateUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.ImportProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.SetActiveProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.UpdateAntiDetectUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.UpdateSplitTunnelUseCase
import io.nikdmitryuk.ultraclient.presentation.platform.AndroidClipboardReader
import io.nikdmitryuk.ultraclient.presentation.platform.AndroidInstalledAppsProvider
import io.nikdmitryuk.ultraclient.presentation.platform.ClipboardReader
import io.nikdmitryuk.ultraclient.presentation.platform.InstalledAppsProvider
import org.koin.dsl.module

val androidAppModule =
    module {
        single<ClipboardReader> { AndroidClipboardReader(get()) }
        single<InstalledAppsProvider> { AndroidInstalledAppsProvider(get()) }

        factory { ConnectVpnUseCase(get(), get(), get()) }
        factory { DisconnectVpnUseCase(get()) }
        factory { ImportProfileUseCase(get(), get()) }
        factory { GetVpnStateUseCase(get()) }
        factory { GetProfilesUseCase(get()) }
        factory { DeleteProfileUseCase(get()) }
        factory { SetActiveProfileUseCase(get()) }
        factory { UpdateSplitTunnelUseCase(get()) }
        factory { UpdateAntiDetectUseCase(get()) }
    }

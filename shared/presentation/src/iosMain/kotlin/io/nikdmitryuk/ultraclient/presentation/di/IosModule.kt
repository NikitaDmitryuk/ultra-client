package io.nikdmitryuk.ultraclient.presentation.di

import io.nikdmitryuk.ultraclient.data.di.dataModule
import io.nikdmitryuk.ultraclient.data.di.platformDataModule
import io.nikdmitryuk.ultraclient.domain.usecase.ConnectVpnUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.DeleteProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.DisconnectVpnUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.GetProfilesUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.GetVpnStateUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.ImportProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.SetActiveProfileUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.UpdateAntiDetectUseCase
import io.nikdmitryuk.ultraclient.domain.usecase.UpdateSplitTunnelUseCase
import io.nikdmitryuk.ultraclient.presentation.platform.ClipboardReader
import io.nikdmitryuk.ultraclient.presentation.platform.InstalledAppsProvider
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun initKoinIos(
    clipboardReader: ClipboardReader,
    installedAppsProvider: InstalledAppsProvider,
) {
    startKoin {
        modules(
            platformDataModule,
            dataModule,
            presentationModule,
            module {
                single { clipboardReader }
                single { installedAppsProvider }
                factory { ConnectVpnUseCase(get(), get(), get()) }
                factory { DisconnectVpnUseCase(get()) }
                factory { ImportProfileUseCase(get(), get()) }
                factory { GetVpnStateUseCase(get()) }
                factory { GetProfilesUseCase(get()) }
                factory { DeleteProfileUseCase(get()) }
                factory { SetActiveProfileUseCase(get()) }
                factory { UpdateSplitTunnelUseCase(get()) }
                factory { UpdateAntiDetectUseCase(get()) }
            },
        )
    }
}

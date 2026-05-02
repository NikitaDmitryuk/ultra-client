package io.nikdmitryuk.ultraclient.data.di

import io.nikdmitryuk.ultraclient.data.local.DatabaseDriverFactory
import io.nikdmitryuk.ultraclient.data.vpn.PlatformVpnEngine
import io.nikdmitryuk.ultraclient.domain.vpn.VpnEngine
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDataModule: Module =
    module {
        single { DatabaseDriverFactory(get()) }
        single<VpnEngine> { PlatformVpnEngine(get()) }
    }

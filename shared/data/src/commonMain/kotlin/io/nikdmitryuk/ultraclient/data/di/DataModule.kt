package io.nikdmitryuk.ultraclient.data.di

import io.nikdmitryuk.ultraclient.data.local.AntiDetectLocalDataSource
import io.nikdmitryuk.ultraclient.data.local.DatabaseDriverFactory
import io.nikdmitryuk.ultraclient.data.local.VpnProfileLocalDataSource
import io.nikdmitryuk.ultraclient.data.local.db.UltraClientDatabase
import io.nikdmitryuk.ultraclient.data.repository.AntiDetectRepositoryImpl
import io.nikdmitryuk.ultraclient.data.remote.ClientExitApi
import io.nikdmitryuk.ultraclient.data.repository.ExitLocationRepositoryImpl
import io.nikdmitryuk.ultraclient.data.repository.VpnProfileRepositoryImpl
import io.nikdmitryuk.ultraclient.data.vpn.SingBoxConfigBuilder
import io.nikdmitryuk.ultraclient.data.vpn.VlessUrlParser
import io.nikdmitryuk.ultraclient.domain.parser.VpnUrlParser
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository
import io.nikdmitryuk.ultraclient.domain.repository.ExitLocationRepository
import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val dataModule =
    module {
        single { UltraClientDatabase(get<DatabaseDriverFactory>().createDriver()) }
        single { VpnProfileLocalDataSource(get()) }
        single { AntiDetectLocalDataSource(get()) }
        single { ClientExitApi() }
        single<VpnProfileRepository> { VpnProfileRepositoryImpl(get()) }
        single<AntiDetectRepository> { AntiDetectRepositoryImpl(get()) }
        single<ExitLocationRepository> { ExitLocationRepositoryImpl(get()) }
        single<VpnUrlParser> { VlessUrlParser() }
        single { SingBoxConfigBuilder() }
    }

expect val platformDataModule: Module

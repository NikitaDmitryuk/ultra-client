package io.nikdmitryuk.ultraclient.data.di

import io.nikdmitryuk.ultraclient.data.antidetect.PortRandomizer
import io.nikdmitryuk.ultraclient.data.local.AntiDetectLocalDataSource
import io.nikdmitryuk.ultraclient.data.local.DatabaseDriverFactory
import io.nikdmitryuk.ultraclient.data.local.VpnProfileLocalDataSource
import io.nikdmitryuk.ultraclient.data.local.db.UltraClientDatabase
import io.nikdmitryuk.ultraclient.data.repository.AntiDetectRepositoryImpl
import io.nikdmitryuk.ultraclient.data.repository.VpnProfileRepositoryImpl
import io.nikdmitryuk.ultraclient.data.vpn.VlessUrlParser
import io.nikdmitryuk.ultraclient.data.vpn.XrayConfigBuilder
import io.nikdmitryuk.ultraclient.domain.parser.VpnUrlParser
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository
import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val dataModule =
    module {
        single { UltraClientDatabase(get<DatabaseDriverFactory>().createDriver()) }
        single { VpnProfileLocalDataSource(get()) }
        single { AntiDetectLocalDataSource(get()) }
        single<VpnProfileRepository> { VpnProfileRepositoryImpl(get()) }
        single<AntiDetectRepository> { AntiDetectRepositoryImpl(get()) }
        single<VpnUrlParser> { VlessUrlParser() }
        single { XrayConfigBuilder() }
        single { PortRandomizer() }
    }

expect val platformDataModule: Module

package io.nikdmitryuk.ultraclient.presentation.di

import io.nikdmitryuk.ultraclient.presentation.screen.home.HomeScreenModel
import io.nikdmitryuk.ultraclient.presentation.screen.profiles.ProfilesScreenModel
import io.nikdmitryuk.ultraclient.presentation.screen.settings.SettingsScreenModel
import org.koin.dsl.module

val presentationModule = module {
    factory { HomeScreenModel(get(), get(), get(), get()) }
    factory { ProfilesScreenModel(get(), get(), get(), get(), get()) }
    factory { SettingsScreenModel(get(), get(), get(), get()) }
}

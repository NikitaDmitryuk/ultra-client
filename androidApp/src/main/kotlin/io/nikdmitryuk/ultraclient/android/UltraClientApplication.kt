package io.nikdmitryuk.ultraclient.android

import android.app.Application
import io.nikdmitryuk.ultraclient.android.di.androidAppModule
import io.nikdmitryuk.ultraclient.data.di.dataModule
import io.nikdmitryuk.ultraclient.data.di.platformDataModule
import io.nikdmitryuk.ultraclient.presentation.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class UltraClientApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@UltraClientApplication)
            modules(
                platformDataModule,
                dataModule,
                presentationModule,
                androidAppModule,
            )
        }
    }
}

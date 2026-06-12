package io.nikdmitryuk.ultraclient.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.nikdmitryuk.ultraclient.data.di.dataModule
import io.nikdmitryuk.ultraclient.data.di.platformDataModule
import io.nikdmitryuk.ultraclient.presentation.App
import io.nikdmitryuk.ultraclient.presentation.di.presentationModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(
            platformDataModule,
            dataModule,
            presentationModule,
            desktopAppModule,
        )
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Ultra Client",
        ) {
            App()
        }
    }
}

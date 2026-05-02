package io.nikdmitryuk.ultraclient.presentation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import io.nikdmitryuk.ultraclient.presentation.screen.home.HomeScreen
import io.nikdmitryuk.ultraclient.presentation.theme.UltraTheme

@Composable
fun App() {
    UltraTheme {
        Navigator(HomeScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}

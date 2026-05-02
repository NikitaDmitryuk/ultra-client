package io.nikdmitryuk.ultraclient.presentation.platform

import androidx.compose.runtime.compositionLocalOf

val LocalVpnPermissionRequester = compositionLocalOf<((Boolean) -> Unit) -> Unit> { { it(true) } }

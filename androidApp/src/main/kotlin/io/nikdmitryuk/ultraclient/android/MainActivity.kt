package io.nikdmitryuk.ultraclient.android

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import io.nikdmitryuk.ultraclient.presentation.App
import io.nikdmitryuk.ultraclient.presentation.platform.LocalVpnPermissionRequester

class MainActivity : ComponentActivity() {
    private val vpnPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                vpnPermissionGrantedCallback?.invoke(true)
            } else {
                vpnPermissionGrantedCallback?.invoke(false)
            }
            vpnPermissionGrantedCallback = null
        }

    var vpnPermissionGrantedCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(
                LocalVpnPermissionRequester provides { onResult -> requestVpnPermission(onResult) },
            ) {
                App()
            }
        }
    }

    fun requestVpnPermission(onResult: (Boolean) -> Unit) {
        val intent = VpnService.prepare(this)
        if (intent == null) {
            onResult(true)
        } else {
            vpnPermissionGrantedCallback = onResult
            vpnPermissionLauncher.launch(intent)
        }
    }
}

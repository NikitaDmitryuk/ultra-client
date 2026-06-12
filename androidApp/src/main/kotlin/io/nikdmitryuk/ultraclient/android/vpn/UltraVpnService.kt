package io.nikdmitryuk.ultraclient.android.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import io.nikdmitryuk.ultraclient.android.BuildConfig
import io.nikdmitryuk.ultraclient.android.MainActivity
import io.nikdmitryuk.ultraclient.data.vpn.SingBoxConfigBuilder
import io.nikdmitryuk.ultraclient.data.vpn.SingBoxOptions
import io.nikdmitryuk.ultraclient.data.vpn.VpnStateHolder
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class UltraVpnService : VpnService() {
    companion object {
        const val ACTION_CONNECT = "io.nikdmitryuk.ultraclient.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "io.nikdmitryuk.ultraclient.ACTION_DISCONNECT"
        const val EXTRA_VLESS_CONFIG = "vless_config_json"
        const val EXTRA_ANTI_DETECT = "anti_detect_json"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ultra_vpn_channel"
        private const val TAG = "UltraVpnService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val vlessJson = intent.getStringExtra(EXTRA_VLESS_CONFIG) ?: return START_NOT_STICKY
                val antiDetectJson = intent.getStringExtra(EXTRA_ANTI_DETECT) ?: return START_NOT_STICKY
                val vlessConfig = json.decodeFromString(VlessConfig.serializer(), vlessJson)
                val antiDetect = json.decodeFromString(AntiDetectConfig.serializer(), antiDetectJson)
                startTunnel(vlessConfig, antiDetect)
            }
            ACTION_DISCONNECT -> stopTunnel()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        super.onRevoke()
        SingBoxBridge.stop()
        VpnStateHolder.emit(VpnState.Disconnected)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        SingBoxBridge.stop()
        serviceScope.cancel()
        wakeLock?.release()
    }

    private fun startTunnel(
        vlessConfig: VlessConfig,
        antiDetect: AntiDetectConfig,
    ) {
        try {
            if (!BuildConfig.SING_BOX_ENABLED) error("sing-box runtime is disabled")
            startSingBoxTunnelBody(vlessConfig, antiDetect)
        } catch (t: Throwable) {
            Log.e(TAG, "startTunnel failed", t)
            VpnStateHolder.emit(VpnState.Error(t.message ?: t.javaClass.simpleName))
            stopSelf()
        }
    }

    private fun startSingBoxTunnelBody(
        vlessConfig: VlessConfig,
        antiDetect: AntiDetectConfig,
    ) {
        val effectiveAntiDetect =
            antiDetect.copy(
                killSwitchEnabled = false,
                fakeDnsEnabled = true,
                randomPortEnabled = false,
            )

        val logPath =
            if (BuildConfig.DEBUG) File(cacheDir, "sing-box.log").absolutePath else null
        val singBoxConfig =
            SingBoxConfigBuilder().build(
                vlessConfig = vlessConfig,
                antiDetect = effectiveAntiDetect,
                options =
                    SingBoxOptions(
                        interfaceName = "ultra0",
                        logLevel = if (BuildConfig.DEBUG) "debug" else "warn",
                        logPath = logPath,
                        autoDetectInterface = false,
                    ),
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        if (BuildConfig.DEBUG) {
            File(cacheDir, "sing-box-config.json").writeText(singBoxConfig)
        }
        val startError =
            SingBoxBridge.start(
                configJson = singBoxConfig,
                antiDetect = effectiveAntiDetect,
                cacheDir = cacheDir,
                vpn = this,
            )
        if (startError != null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            error(startError)
        }
        Log.i(TAG, "sing-box ready inbound=tun")
        VpnStateHolder.emit(VpnState.Connected(vlessConfig.address, System.currentTimeMillis()))
        startWatchdog()
    }

    private fun stopTunnel() {
        SingBoxBridge.stop()
        VpnStateHolder.emit(VpnState.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startWatchdog() {
        serviceScope.launch {
            while (true) {
                delay(5_000)
                if (!SingBoxBridge.isRunning()) {
                    VpnStateHolder.emit(VpnState.Error("sing-box process terminated unexpectedly"))
                    stopTunnel()
                    break
                }
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            pm
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ultra-client:VpnWakeLock",
                ).also { it.acquire(24 * 60 * 60 * 1000L) }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows VPN connection status" }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("ultra-client")
            .setContentText("VPN connected")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}

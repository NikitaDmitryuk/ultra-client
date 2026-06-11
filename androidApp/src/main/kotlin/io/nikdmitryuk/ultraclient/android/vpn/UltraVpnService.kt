package io.nikdmitryuk.ultraclient.android.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import io.nikdmitryuk.ultraclient.android.BuildConfig
import io.nikdmitryuk.ultraclient.android.MainActivity
import io.nikdmitryuk.ultraclient.data.vpn.VpnStateHolder
import io.nikdmitryuk.ultraclient.data.vpn.XrayConfigBuilder
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

        private const val READINESS_POLL_MS = 250L
        private const val READINESS_TIMEOUT_MS = 5_000L

        /** Минимум времени в Connecting после старта проверки готовности, чтобы UI не мигал. */
        private const val MIN_CONNECTING_VISIBLE_MS = 800L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunFd: ParcelFileDescriptor? = null
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
        XrayBridge.stopXray()
        closeTun()
        VpnStateHolder.emit(VpnState.Disconnected)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        XrayBridge.stopXray()
        closeTun()
        serviceScope.cancel()
        wakeLock?.release()
    }

    private fun startTunnel(
        vlessConfig: VlessConfig,
        antiDetect: AntiDetectConfig,
    ) {
        try {
            startTunnelBody(vlessConfig, antiDetect)
        } catch (t: Throwable) {
            Log.e(TAG, "startTunnel failed", t)
            VpnStateHolder.emit(VpnState.Error(t.message ?: t.javaClass.simpleName))
            stopSelf()
        }
    }

    private fun startTunnelBody(
        vlessConfig: VlessConfig,
        antiDetect: AntiDetectConfig,
    ) {
        val effectiveAntiDetect =
            antiDetect.copy(
                killSwitchEnabled = false,
                fakeDnsEnabled = true,
                randomPortEnabled = false,
            )

        val xrayErrorLogPath =
            if (BuildConfig.DEBUG) File(cacheDir, "xray-error.log").absolutePath else null
        if (xrayErrorLogPath != null) {
            Log.i(TAG, "Xray error log (debug): $xrayErrorLogPath")
        }

        val xrayConfig =
            XrayConfigBuilder().build(
                vlessConfig,
                effectiveAntiDetect,
                0,
                0,
                xrayErrorLogPath,
            )

        val allowedCount = effectiveAntiDetect.vpnIncludedApps.count { it.throughVpn }
        val legacyBypassCount = effectiveAntiDetect.legacyBypassAppIds.size
        val routingMode =
            when {
                allowedCount > 0 -> "allow_list(count=$allowedCount)"
                legacyBypassCount > 0 -> "legacy_bypass(count=$legacyBypassCount)"
                else -> "full_tunnel"
            }
        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "startTunnel server=${vlessConfig.address}:${vlessConfig.port} " +
                    "inbound=tun fakeDns=${effectiveAntiDetect.fakeDnsEnabled} routing=$routingMode",
            )
        } else {
            Log.i(TAG, "startTunnel inbound=tun fakeDns=${effectiveAntiDetect.fakeDnsEnabled} routing=$routingMode")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        val tun = TunConfigurator(this).establish(effectiveAntiDetect)
        tunFd = tun
        Log.i(TAG, "TUN fd=${tun.fd} established inbound=tun")

        val datDir = GeoDataInstaller.ensureInstalled(this)
        if (BuildConfig.DEBUG) {
            val configFile = File(cacheDir, "xray-config.json")
            configFile.writeText(xrayConfig)
            val preflightTun = ParcelFileDescriptor.dup(tun.fileDescriptor)
            val configError =
                try {
                    XrayBridge.testXrayConfigPath(
                        datDir = datDir,
                        configPath = configFile.absolutePath,
                        tunFd = preflightTun.fd,
                    )
                } finally {
                    runCatching { preflightTun.close() }
                }
            if (configError != null) {
                Log.e(TAG, "Xray config preflight failed: $configError")
                closeTun()
                VpnStateHolder.emit(VpnState.Error("Xray config invalid: $configError"))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            Log.i(TAG, "Xray config preflight OK inbound=tun originalFd=${tun.fd} path=${configFile.absolutePath}")
        }
        val xrayError = XrayBridge.startXray(xrayConfig, tun.fd, datDir, this)
        if (xrayError == null) {
            Log.i(TAG, "startXray returned OK, awaiting readiness (poll=${READINESS_POLL_MS}ms timeout=${READINESS_TIMEOUT_MS}ms)")
            serviceScope.launch {
                val readinessStartedAt = SystemClock.elapsedRealtime()
                val ready =
                    XrayBridge.awaitCoreReady(
                        pollIntervalMs = READINESS_POLL_MS,
                        timeoutMs = READINESS_TIMEOUT_MS,
                    )
                if (!ready) {
                    Log.e(TAG, "Xray readiness failed — getXrayState did not become true in time")
                    XrayBridge.stopXray()
                    closeTun()
                    VpnStateHolder.emit(
                        VpnState.Error("Xray did not become ready — check node and xray-error.log (debug)"),
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                val elapsed = SystemClock.elapsedRealtime() - readinessStartedAt
                val padMs = (MIN_CONNECTING_VISIBLE_MS - elapsed).coerceAtLeast(0L)
                if (padMs > 0) {
                    delay(padMs)
                }
                Log.i(TAG, "Xray ready inbound=tun (readiness ${elapsed}ms + ui_pad ${padMs}ms)")
                VpnStateHolder.emit(VpnState.Connected(vlessConfig.address, System.currentTimeMillis()))
                startWatchdog()
            }
        } else {
            Log.e(TAG, "startXray failed: $xrayError")
            VpnStateHolder.emit(VpnState.Error(xrayError))
            stopSelf()
        }
    }

    private fun stopTunnel() {
        XrayBridge.stopXray()
        closeTun()
        VpnStateHolder.emit(VpnState.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startWatchdog() {
        serviceScope.launch {
            while (true) {
                delay(5_000)
                if (!XrayBridge.isRunning()) {
                    VpnStateHolder.emit(VpnState.Error("Xray process terminated unexpectedly"))
                    stopTunnel()
                    break
                }
            }
        }
    }

    private fun closeTun() {
        tunFd?.close()
        tunFd = null
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

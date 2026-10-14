package com.ankiride.bridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ankiride.bridge.anki.AnkiActionDispatcherImpl
import com.ankiride.bridge.ble.ZwiftBleController
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.BleConnectionState
import com.ankiride.bridge.mywhoosh.MyWhooshNetworkDispatcher
import com.ankiride.bridge.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BridgeForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ankiride_bridge_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.ankiride.bridge.START"
        const val ACTION_STOP = "com.ankiride.bridge.STOP"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _connectionStateFlow = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
        val connectionStateFlow: StateFlow<BleConnectionState> = _connectionStateFlow.asStateFlow()

        var currentConfig: AppConfiguration = AppConfiguration()
            private set

        fun startService(context: Context, config: AppConfiguration) {
            currentConfig = config
            val intent = Intent(context, BridgeForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BridgeForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var bleController: ZwiftBleController? = null
    private var myWhooshDispatcher: MyWhooshNetworkDispatcher? = null
    private var ankiDispatcher: AnkiActionDispatcherImpl? = null
    private var actionRouter: ActionRouter? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopBridge()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        startBridge()
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AnkiRide Bridge Running")
            .setContentText("Routing Zwift Click to AnkiDroid & MyWhoosh")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startBridge() {
        _isRunningFlow.value = true

        val ble = ZwiftBleController(this, serviceScope)
        val whoosh = MyWhooshNetworkDispatcher(serviceProvider = { BridgeAccessibilityService.activeInstance })
        val anki = AnkiActionDispatcherImpl(serviceProvider = { BridgeAccessibilityService.activeInstance })
        val router = ActionRouter(
            bleController = ble,
            ankiDispatcher = anki,
            myWhooshDispatcher = whoosh,
            config = currentConfig
        )

        bleController = ble
        myWhooshDispatcher = whoosh
        ankiDispatcher = anki
        actionRouter = router

        serviceScope.launch {
            ble.connectionState.collect { state ->
                _connectionStateFlow.value = state
            }
        }

        router.start(serviceScope)
        ble.start(currentConfig)
    }

    private fun stopBridge() {
        actionRouter?.stop()
        bleController?.stop()
        myWhooshDispatcher?.close()

        bleController = null
        myWhooshDispatcher = null
        ankiDispatcher = null
        actionRouter = null

        _isRunningFlow.value = false
        _connectionStateFlow.value = BleConnectionState.Disconnected
    }

    override fun onDestroy() {
        stopBridge()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AnkiRide Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps AnkiRide BLE routing alive in background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

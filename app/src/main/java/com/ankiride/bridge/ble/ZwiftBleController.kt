package com.ankiride.bridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.BleConnectionState
import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickBleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class ZwiftBleController(
    private val context: Context,
    private val externalScope: CoroutineScope? = null
) : ClickBleController {

    companion object {
        private const val TAG = "ZwiftBleController"

        // Service UUIDs
        val SERVICE_NEW_UUID: UUID = UUID.fromString("0000fc82-0000-1000-8000-00805f9b34fb")
        val SERVICE_LEGACY_UUID: UUID = UUID.fromString("00000001-19ca-4651-86e5-fa29dcdd09d1")

        // Characteristic UUIDs
        val MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00000002-19ca-4651-86e5-fa29dcdd09d1")
        val CONTROL_POINT_CHAR_UUID: UUID = UUID.fromString("00000003-19ca-4651-86e5-fa29dcdd09d1")
        val COMMAND_RESPONSE_CHAR_UUID: UUID = UUID.fromString("00000004-19ca-4651-86e5-fa29dcdd09d1")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Battery Service & Characteristic
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        // Handshake payload: "RideOn" ASCII
        val RIDE_ON_HANDSHAKE = byteArrayOf(0x52, 0x69, 0x64, 0x65, 0x4F, 0x6E)

        // Manufacturer Specific Data
        const val ZWIFT_MANUFACTURER_ID = 0x094A // 2378 decimal
        const val POD_CLICK_SINGLE = 0x09
        const val POD_RIGHT_V2 = 0x07
        const val POD_LEFT_V2 = 0x08
        const val POD_PLAY_RIGHT = 0x02
        const val POD_PLAY_LEFT = 0x03
    }

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gestureEngine = ZwiftGestureEngine(scope)

    override val gestureEvents: SharedFlow<ButtonGestureEvent> = gestureEngine.gestureEvents

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private var config: AppConfiguration = AppConfiguration()
    private var isRunning = false

    private var leftGatt: BluetoothGatt? = null
    private var rightGatt: BluetoothGatt? = null
    private var isLeftConnected = false
    private var isRightConnected = false
    private var latestBatteryPercent: Int? = null

    private var leftLastHeartbeatTime = 0L
    private var leftWatchdogJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            handleDiscoveredDevice(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleDiscoveredDevice(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            _connectionState.value = BleConnectionState.Error("Scan failed: $errorCode")
        }
    }

    override fun start(config: AppConfiguration) {
        this.config = config
        gestureEngine.rightPodOnlyMode = config.rightPodOnlyMode
        isRunning = true
        _connectionState.value = BleConnectionState.Scanning

        if (!hasRequiredPermissions()) {
            _connectionState.value = BleConnectionState.Error("Missing required Bluetooth permissions")
            return
        }

        startScanning()
        startLeftPodWatchdog()
    }

    override fun stop() {
        isRunning = false
        stopScanning()
        leftWatchdogJob?.cancel()
        leftWatchdogJob = null

        scope.launch {
            gestureEngine.reset()
        }

        disconnectGatt(leftGatt)
        disconnectGatt(rightGatt)
        leftGatt = null
        rightGatt = null
        isLeftConnected = false
        isRightConnected = false
        _connectionState.value = BleConnectionState.Disconnected
    }

    private fun hasRequiredPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            return scanGranted && connectGranted
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_NEW_UUID)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_LEGACY_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            _connectionState.value = BleConnectionState.Error("Unable to start scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan", e)
        }
    }

    private fun handleDiscoveredDevice(result: ScanResult) {
        val device = result.device ?: return
        val scanRecord = result.scanRecord ?: return

        val mfgData = scanRecord.getManufacturerSpecificData(ZWIFT_MANUFACTURER_ID)
        val podType = mfgData?.firstOrNull()?.toInt()

        val isLeftPod = podType == POD_LEFT_V2 || podType == POD_PLAY_LEFT
        val isRightPod = podType == POD_RIGHT_V2 || podType == POD_PLAY_RIGHT || podType == POD_CLICK_SINGLE

        // MAC pinning checks
        val deviceAddress = device.address
        if (isLeftPod) {
            if (config.rightPodOnlyMode) return // Ignore left pod in rightPodOnlyMode
            if (config.pinnedLeftPodMac != null && !config.pinnedLeftPodMac.equals(deviceAddress, ignoreCase = true)) {
                return // Discard unpinned left pod
            }
            if (leftGatt == null && !isLeftConnected) {
                connectToDevice(device, isLeft = true)
            }
        } else if (isRightPod || podType == null) {
            if (config.pinnedRightPodMac != null && !config.pinnedRightPodMac.equals(deviceAddress, ignoreCase = true)) {
                return // Discard unpinned right pod
            }
            if (rightGatt == null && !isRightConnected) {
                connectToDevice(device, isLeft = false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice, isLeft: Boolean) {
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to ${device.address} (isLeft=$isLeft)")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from ${device.address} (isLeft=$isLeft)")
                    if (isLeft) {
                        isLeftConnected = false
                        leftGatt = null
                    } else {
                        isRightConnected = false
                        rightGatt = null
                    }
                    updateConnectionState()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    performHandshake(gatt, isLeft)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleCharacteristicData(gatt, characteristic.uuid, value, isLeft)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                handleCharacteristicData(gatt, characteristic.uuid, value, isLeft)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    val battery = value.firstOrNull()?.toInt()
                    if (battery != null) {
                        latestBatteryPercent = battery
                        updateConnectionState()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    val battery = value.firstOrNull()?.toInt()
                    if (battery != null) {
                        latestBatteryPercent = battery
                        updateConnectionState()
                    }
                }
            }
        }

        val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (isLeft) {
            leftGatt = gatt
        } else {
            rightGatt = gatt
        }
    }

    @SuppressLint("MissingPermission")
    private fun performHandshake(gatt: BluetoothGatt, isLeft: Boolean) {
        val zwiftService = gatt.getService(SERVICE_NEW_UUID) ?: gatt.getService(SERVICE_LEGACY_UUID)
        if (zwiftService == null) {
            Log.e(TAG, "Zwift service not found on device ${gatt.device.address}")
            return
        }

        // 1. Enable Indications on COMMAND_RESPONSE_CHAR_UUID
        val cmdRespChar = zwiftService.getCharacteristic(COMMAND_RESPONSE_CHAR_UUID)
        if (cmdRespChar != null) {
            gatt.setCharacteristicNotification(cmdRespChar, true)
            val cccd = cmdRespChar.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }
        }

        // 2. Enable Notifications on MEASUREMENT_CHAR_UUID
        val measureChar = zwiftService.getCharacteristic(MEASUREMENT_CHAR_UUID)
        if (measureChar != null) {
            gatt.setCharacteristicNotification(measureChar, true)
            val cccd = measureChar.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }
        }

        // 3. Write "RideOn" handshake to CONTROL_POINT_CHAR_UUID
        val ctrlChar = zwiftService.getCharacteristic(CONTROL_POINT_CHAR_UUID)
        if (ctrlChar != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    ctrlChar,
                    RIDE_ON_HANDSHAKE,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                ctrlChar.value = RIDE_ON_HANDSHAKE
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(ctrlChar)
            }
        }

        // Check for Battery Service to read initial battery %
        val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
        val batteryLevelChar = batteryService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
        if (batteryLevelChar != null) {
            gatt.readCharacteristic(batteryLevelChar)
        }

        if (isLeft) {
            isLeftConnected = true
            leftLastHeartbeatTime = System.currentTimeMillis()
        } else {
            isRightConnected = true
        }
        updateConnectionState()
    }

    private fun handleCharacteristicData(
        gatt: BluetoothGatt,
        charUuid: UUID,
        value: ByteArray,
        isLeft: Boolean
    ) {
        if (charUuid == COMMAND_RESPONSE_CHAR_UUID) {
            val responseText = String(value, Charsets.US_ASCII)
            if (responseText.startsWith("RideOn")) {
                Log.d(TAG, "Zwift Handshake verified for ${gatt.device.address}")
            }
            return
        }

        if (charUuid == MEASUREMENT_CHAR_UUID) {
            if (value.isNotEmpty()) {
                val msgId = value[0]
                // Keep-alives 0x15 or 0x19
                if (msgId == 0x15.toByte() || msgId == 0x19.toByte()) {
                    if (isLeft) {
                        leftLastHeartbeatTime = System.currentTimeMillis()
                    }
                    return
                }

                if (msgId == ZwiftPacketParser.MSG_KEYPAD_STATUS) {
                    if (isLeft) {
                        leftLastHeartbeatTime = System.currentTimeMillis()
                    }
                    val activeMask = ZwiftPacketParser.parseActiveButtonMask(value)
                    if (activeMask != null) {
                        val pressedButtons = ZwiftPacketParser.getPressedButtons(activeMask)
                        scope.launch {
                            gestureEngine.onActiveButtonsChanged(pressedButtons)
                        }
                    }
                }
            }
        }
    }

    private fun updateConnectionState() {
        if (!isLeftConnected && !isRightConnected) {
            _connectionState.value = if (isRunning) BleConnectionState.Scanning else BleConnectionState.Disconnected
        } else {
            _connectionState.value = BleConnectionState.Connected(
                leftPodConnected = isLeftConnected,
                rightPodConnected = isRightConnected,
                batteryPercent = latestBatteryPercent
            )
        }
    }

    private fun startLeftPodWatchdog() {
        leftWatchdogJob?.cancel()
        leftWatchdogJob = scope.launch {
            while (isActive && isRunning) {
                delay(5000L)
                if (!config.rightPodOnlyMode && isLeftConnected) {
                    val timeoutMs = config.leftPodAutoReconnectSec * 1000L
                    val timeSinceHeartbeat = System.currentTimeMillis() - leftLastHeartbeatTime
                    if (timeSinceHeartbeat > timeoutMs) {
                        Log.w(TAG, "Left pod watchdog timeout ($timeSinceHeartbeat ms). Gracefully reconnecting...")
                        reconnectLeftPod()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun reconnectLeftPod() {
        val gatt = leftGatt
        if (gatt != null) {
            try {
                gatt.disconnect()
                delay(150L)
                gatt.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Error during left pod reconnect", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt(gatt: BluetoothGatt?) {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT", e)
        }
    }
}

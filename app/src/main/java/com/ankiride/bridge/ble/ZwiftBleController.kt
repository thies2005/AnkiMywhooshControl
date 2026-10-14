package com.ankiride.bridge.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.ankiride.bridge.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class ZwiftBleController(
    private val context: Context,
    private val scope: CoroutineScope
) : ClickBleController {

    companion object {
        val ZWIFT_FC82_SERVICE_UUID: UUID = UUID.fromString("0000fc82-0000-1000-8000-00805f9b34fb")
        val ZWIFT_LEGACY_SERVICE_UUID: UUID = UUID.fromString("00000001-19ca-4651-86e5-fa29dcdd09d1")

        val MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00000002-19ca-4651-86e5-fa29dcdd09d1")
        val CONTROL_POINT_CHAR_UUID: UUID = UUID.fromString("00000003-19ca-4651-86e5-fa29dcdd09d1")
        val COMMAND_RESPONSE_CHAR_UUID: UUID = UUID.fromString("00000004-19ca-4651-86e5-fa29dcdd09d1")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val ZWIFT_MANUFACTURER_ID = 0x094A
        const val DEVICE_ID_RIGHT_POD: Byte = 0x07
        const val DEVICE_ID_LEFT_POD: Byte = 0x08
        const val DEVICE_ID_CLICK: Byte = 0x09

        val RIDE_ON_HANDSHAKE = byteArrayOf(0x52, 0x69, 0x64, 0x65, 0x4F, 0x6E) // "RideOn"

        private val _lastRightMac = MutableStateFlow<String?>(null)
        val lastRightMac: StateFlow<String?> = _lastRightMac.asStateFlow()

        private val _lastLeftMac = MutableStateFlow<String?>(null)
        val lastLeftMac: StateFlow<String?> = _lastLeftMac.asStateFlow()
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _gestureEvents = MutableSharedFlow<ButtonGestureEvent>(extraBufferCapacity = 64)
    override val gestureEvents: SharedFlow<ButtonGestureEvent> = _gestureEvents

    private val gestureEngine = ZwiftGestureEngine { event ->
        DiagnosticLogger.info(
            LogCategory.BLE,
            "Gesture detected: button=${event.button.name} (${event.button.displayName}), type=${event.type.name}"
        )
        scope.launch { _gestureEvents.emit(event) }
    }

    private val activeGatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val connectedPods = ConcurrentHashMap<String, Boolean>() // address -> isLeftPod
    private var leftPodWatchdogJob: Job? = null
    private var currentConfig = AppConfiguration()
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val address = device.address ?: return
            if (activeGatts.containsKey(address)) return

            val mfgData = result.scanRecord?.getManufacturerSpecificData(ZWIFT_MANUFACTURER_ID)
            val podType = if (mfgData != null && mfgData.isNotEmpty()) mfgData[0] else null
            val isLeftPod = (podType == DEVICE_ID_LEFT_POD)
            val podLabel = when (podType) {
                DEVICE_ID_RIGHT_POD -> "RightPod(0x07)"
                DEVICE_ID_LEFT_POD -> "LeftPod(0x08)"
                DEVICE_ID_CLICK -> "Click(0x09)"
                null -> "UnknownPod"
                else -> "Pod(0x%02X)".format(podType)
            }

            if (currentConfig.rightPodOnlyNoZwift && isLeftPod) {
                DiagnosticLogger.info(
                    LogCategory.BLE,
                    "Ignored Left Pod $address ($podLabel) because 1-Pod Mode (Right Pod Only) is active"
                )
                return
            }

            val allowedByPin = when {
                !currentConfig.pinnedRightPodMac.isNullOrBlank() && address.equals(currentConfig.pinnedRightPodMac, true) -> true
                !currentConfig.pinnedLeftPodMac.isNullOrBlank() && address.equals(currentConfig.pinnedLeftPodMac, true) -> true
                currentConfig.pinnedRightPodMac.isNullOrBlank() && currentConfig.pinnedLeftPodMac.isNullOrBlank() -> true
                else -> false
            }
            if (!allowedByPin) {
                DiagnosticLogger.warn(LogCategory.BLE, "Skipped unpinned BLE device $address ($podLabel)")
                return
            }

            DiagnosticLogger.info(
                LogCategory.BLE,
                "Discovered Zwift controller: name=${device.name ?: "N/A"}, mac=$address, pod=$podLabel, rssi=${result.rssi}dBm"
            )
            connectToDevice(device, isLeftPod = isLeftPod)
        }

        override fun onScanFailed(errorCode: Int) {
            DiagnosticLogger.error(LogCategory.BLE, "BLE Scan failed with errorCode=$errorCode")
            _connectionState.value = BleConnectionState.ERROR
        }
    }

    override fun startScanAndConnect(config: AppConfiguration) {
        currentConfig = config
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            DiagnosticLogger.error(LogCategory.BLE, "Bluetooth adapter is disabled or unavailable")
            _connectionState.value = BleConnectionState.ERROR
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            DiagnosticLogger.error(LogCategory.BLE, "BluetoothLeScanner is null")
            _connectionState.value = BleConnectionState.ERROR
            return
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(ZWIFT_FC82_SERVICE_UUID)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(ZWIFT_LEGACY_SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        DiagnosticLogger.info(
            LogCategory.BLE,
            "Starting BLE scan (1-Pod Right-Only=${config.rightPodOnlyNoZwift}, UUIDs=0xFC82 + Legacy)"
        )
        _connectionState.value = BleConnectionState.SCANNING
        isScanning = true
        scanner.startScan(filters, settings, scanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice, isLeftPod: Boolean) {
        _connectionState.value = BleConnectionState.CONNECTING
        DiagnosticLogger.info(LogCategory.BLE, "Connecting GATT to ${device.address} (isLeftPod=$isLeftPod)...")
        val callback = createGattCallback(device, isLeftPod)
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
        if (gatt != null) {
            activeGatts[device.address] = gatt
        }
    }

    private fun updateConnectionState() {
        val hasRight = connectedPods.values.any { !it }
        val hasLeft = connectedPods.values.any { it }
        val nextState = when {
            hasRight && hasLeft -> BleConnectionState.CONNECTED_BOTH_PODS
            hasRight || hasLeft -> BleConnectionState.CONNECTED_RIGHT_ONLY
            activeGatts.isEmpty() -> BleConnectionState.DISCONNECTED
            else -> BleConnectionState.CONNECTING
        }
        _connectionState.value = nextState
        DiagnosticLogger.info(LogCategory.BLE, "BLE state updated -> $nextState (right=$hasRight, left=$hasLeft)")
    }

    private fun createGattCallback(device: BluetoothDevice, isLeftPod: Boolean) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && gatt != null) {
                DiagnosticLogger.info(LogCategory.BLE, "GATT connected to ${device.address} (status=$status). Discovering services...")
                _connectionState.value = BleConnectionState.HANDSHAKING
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                DiagnosticLogger.warn(LogCategory.BLE, "GATT disconnected from ${device.address} (status=$status)")
                gatt?.close()
                activeGatts.remove(device.address)
                connectedPods.remove(device.address)
                updateConnectionState()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                DiagnosticLogger.error(LogCategory.BLE, "Service discovery failed on ${device.address} (status=$status)")
                _connectionState.value = BleConnectionState.ERROR
                return
            }
            val service = gatt.getService(ZWIFT_FC82_SERVICE_UUID)
                ?: gatt.getService(ZWIFT_LEGACY_SERVICE_UUID)
                ?: run {
                    DiagnosticLogger.error(LogCategory.BLE, "Neither 0xFC82 nor legacy Zwift service found on ${device.address}")
                    _connectionState.value = BleConnectionState.ERROR
                    return
                }

            DiagnosticLogger.info(LogCategory.BLE, "Found Zwift GATT service ${service.uuid} on ${device.address}")
            val notifyChar = service.getCharacteristic(MEASUREMENT_CHAR_UUID) ?: run {
                DiagnosticLogger.error(LogCategory.BLE, "Measurement char 0002 not found on ${device.address}")
                return
            }
            gatt.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                DiagnosticLogger.info(LogCategory.BLE, "Enabling CCCD notifications on ${device.address}...")
                writeDescriptorCompat(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                writeRideOnHandshake(gatt, service)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (gatt == null) return
            DiagnosticLogger.info(LogCategory.BLE, "CCCD descriptor write completed (status=$status) on ${device.address}. Sending 'RideOn'...")
            val service = gatt.getService(ZWIFT_FC82_SERVICE_UUID)
                ?: gatt.getService(ZWIFT_LEGACY_SERVICE_UUID)
                ?: return
            writeRideOnHandshake(gatt, service)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (characteristic?.uuid == CONTROL_POINT_CHAR_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    connectedPods[device.address] = isLeftPod
                    if (isLeftPod) {
                        _lastLeftMac.value = device.address
                    } else {
                        _lastRightMac.value = device.address
                    }
                    DiagnosticLogger.info(LogCategory.BLE, "'RideOn' handshake succeeded on ${device.address} (isLeftPod=$isLeftPod)")
                    updateConnectionState()
                    if (isLeftPod && !currentConfig.rightPodOnlyNoZwift && gatt != null) {
                        scheduleLeftPodWatchdog(device)
                    }
                } else {
                    DiagnosticLogger.error(LogCategory.BLE, "'RideOn' write failed with status=$status on ${device.address}")
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleMeasurementNotification(device.address, characteristic.uuid, value)
        }

        @Deprecated("Deprecated in API 33")
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic != null) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                handleMeasurementNotification(device.address, characteristic.uuid, value)
            }
        }
    }

    private fun handleMeasurementNotification(address: String, charUuid: UUID, value: ByteArray) {
        if (charUuid == MEASUREMENT_CHAR_UUID && value.isNotEmpty() && value[0] == ZwiftPacketParser.MSG_KEYPAD_STATUS) {
            val mask = ZwiftPacketParser.parseActiveButtonMask(value)
            if (mask != null && mask != 0) {
                val hex = value.joinToString(" ") { "%02X".format(it) }
                DiagnosticLogger.info(LogCategory.PACKET, "0x23 packet from $address: [$hex] -> activeMask=0x${"%05X".format(mask)}")
            }
            gestureEngine.processButtonStates(ZwiftPacketParser.parse(value))
        }
    }

    private fun writeRideOnHandshake(gatt: BluetoothGatt, service: BluetoothGattService) {
        val controlChar = service.getCharacteristic(CONTROL_POINT_CHAR_UUID) ?: run {
            DiagnosticLogger.error(LogCategory.BLE, "Control point char 0003 missing on ${gatt.device?.address}")
            return
        }
        val writeType = if ((controlChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 &&
            (controlChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0
        ) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(controlChar, RIDE_ON_HANDSHAKE, writeType)
        } else {
            @Suppress("DEPRECATION")
            controlChar.writeType = writeType
            @Suppress("DEPRECATION")
            controlChar.value = RIDE_ON_HANDSHAKE
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(controlChar)
        }
    }

    private fun writeDescriptorCompat(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun scheduleLeftPodWatchdog(leftDevice: BluetoothDevice) {
        leftPodWatchdogJob?.cancel()
        leftPodWatchdogJob = scope.launch {
            delay(currentConfig.leftPodAutoReconnectSec * 1000L)
            DiagnosticLogger.info(LogCategory.BLE, "55s Left-Pod watchdog cycling connection for ${leftDevice.address}...")
            val existing = activeGatts.remove(leftDevice.address)
            connectedPods.remove(leftDevice.address)
            existing?.disconnect()
            existing?.close()
            delay(150L)
            connectToDevice(leftDevice, isLeftPod = true)
        }
    }

    override fun disconnect() {
        DiagnosticLogger.info(LogCategory.BLE, "Disconnecting BLE controller and stopping scan")
        leftPodWatchdogJob?.cancel()
        if (isScanning && bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
        }
        activeGatts.values.forEach {
            it.disconnect()
            it.close()
        }
        activeGatts.clear()
        connectedPods.clear()
        _connectionState.value = BleConnectionState.DISCONNECTED
    }
}

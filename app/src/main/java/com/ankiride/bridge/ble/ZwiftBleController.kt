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
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.BleConnectionState
import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickBleController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _gestureEvents = MutableSharedFlow<ButtonGestureEvent>(extraBufferCapacity = 64)
    override val gestureEvents: SharedFlow<ButtonGestureEvent> = _gestureEvents

    private val gestureEngine = ZwiftGestureEngine { event ->
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

            if (currentConfig.rightPodOnlyNoZwift && isLeftPod) {
                return
            }

            val allowedByPin = when {
                currentConfig.pinnedRightPodMac != null && address.equals(currentConfig.pinnedRightPodMac, true) -> true
                currentConfig.pinnedLeftPodMac != null && address.equals(currentConfig.pinnedLeftPodMac, true) -> true
                currentConfig.pinnedRightPodMac == null && currentConfig.pinnedLeftPodMac == null -> true
                else -> false
            }
            if (!allowedByPin) return

            connectToDevice(device, isLeftPod = isLeftPod)
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.ERROR
        }
    }

    override fun startScanAndConnect(config: AppConfiguration) {
        currentConfig = config
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = BleConnectionState.ERROR
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
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

        _connectionState.value = BleConnectionState.SCANNING
        isScanning = true
        scanner.startScan(filters, settings, scanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice, isLeftPod: Boolean) {
        _connectionState.value = BleConnectionState.CONNECTING
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
        _connectionState.value = when {
            hasRight && hasLeft -> BleConnectionState.CONNECTED_BOTH_PODS
            hasRight || hasLeft -> BleConnectionState.CONNECTED_RIGHT_ONLY
            activeGatts.isEmpty() -> BleConnectionState.DISCONNECTED
            else -> BleConnectionState.CONNECTING
        }
    }

    private fun createGattCallback(device: BluetoothDevice, isLeftPod: Boolean) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && gatt != null) {
                _connectionState.value = BleConnectionState.HANDSHAKING
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt?.close()
                activeGatts.remove(device.address)
                connectedPods.remove(device.address)
                updateConnectionState()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                _connectionState.value = BleConnectionState.ERROR
                return
            }
            val service = gatt.getService(ZWIFT_FC82_SERVICE_UUID)
                ?: gatt.getService(ZWIFT_LEGACY_SERVICE_UUID)
                ?: run {
                    _connectionState.value = BleConnectionState.ERROR
                    return
                }

            val notifyChar = service.getCharacteristic(MEASUREMENT_CHAR_UUID) ?: return
            gatt.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                writeDescriptorCompat(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                writeRideOnHandshake(gatt, service)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (gatt == null) return
            val service = gatt.getService(ZWIFT_FC82_SERVICE_UUID)
                ?: gatt.getService(ZWIFT_LEGACY_SERVICE_UUID)
                ?: return
            writeRideOnHandshake(gatt, service)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (characteristic?.uuid == CONTROL_POINT_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                connectedPods[device.address] = isLeftPod
                updateConnectionState()
                if (isLeftPod && !currentConfig.rightPodOnlyNoZwift && gatt != null) {
                    scheduleLeftPodWatchdog(device)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == MEASUREMENT_CHAR_UUID && value.isNotEmpty() && value[0] == ZwiftPacketParser.MSG_KEYPAD_STATUS) {
                gestureEngine.processButtonStates(ZwiftPacketParser.parse(value))
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic?.uuid == MEASUREMENT_CHAR_UUID) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                if (value.isNotEmpty() && value[0] == ZwiftPacketParser.MSG_KEYPAD_STATUS) {
                    gestureEngine.processButtonStates(ZwiftPacketParser.parse(value))
                }
            }
        }
    }

    private fun writeRideOnHandshake(gatt: BluetoothGatt, service: BluetoothGattService) {
        val controlChar = service.getCharacteristic(CONTROL_POINT_CHAR_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(controlChar, RIDE_ON_HANDSHAKE, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
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
            val existing = activeGatts.remove(leftDevice.address)
            connectedPods.remove(leftDevice.address)
            existing?.disconnect()
            existing?.close()
            delay(150L)
            connectToDevice(leftDevice, isLeftPod = true)
        }
    }

    override fun disconnect() {
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

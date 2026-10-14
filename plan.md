# Standalone Architecture & Master Remediation Specification for `thies2005/AnkiMywhooshControl`

## 1. Executive Architecture: 100% Standalone (No Zwift App, No BikeControl App, Zero Quirks)

This specification upgrades **[thies2005/AnkiMywhooshControl](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fthies2005%2FAnkiMywhooshControl)** into a **100% standalone Android application** that replaces BikeControl completely, never requires the Zwift app, and eliminates every split-screen and button-clicking quirk.

### 1.1 Three Pillars of the Zero-Quirk Standalone Design

1. **Default 1-Controller Mode (Right Pod Only) with Seamless 2-Controller Support**:
   - **Why Default to the Right Pod (`rightPodOnlyNoZwift = true`)**: The [Zwift Click v2](https://www.google.com/search?q=https%3A%2F%2Fzwiftinsider.com%2Fclick-v2-announced%2F) Right Pod (`A`, `B`, `Y`, `Z` diamond + top `+` rocker = 5 physical buttons) has **no 24-hour Zwift server lock** and **never disconnects after 60 seconds** ([BikeControl Click v2 Guide](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fcontroller-zwift-click-v2%2F); [OpenBikeControl Issue #68](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fbikecontrol%2Fissues%2F68)).
   - **Instant Mechanical Shifting on 1 Pod (`useZForShiftDownInSinglePodMode = true`, Default)**: Instead of forcing you to wait for a slow long-press to shift down on a climb, the top two buttons handle shifting (**Top `+` Rocker = Shift Up**, **Top `Z` Button = Shift Down**) with **zero latency**, while the bottom three buttons (**`Y` = Again**, **`B` = Hard**, **`A` = Good**, **Long-Press `A` = Easy**, **Long-Press `Y` = Undo**) handle 100% of AnkiDroid reviews.
   - **Full 2-Controller Mode (`rightPodOnlyNoZwift = false`)**: Flip one switch in the UI to enable the Left Pod (`–` Shift Rocker + 4-way D-Pad). The app automatically manages two concurrent BLE GATT connections, runs a 55-second auto-reconnect watchdog on the Left Pod so you still never need the Zwift app ([OpenBikeControl Issue #68](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fbikecontrol%2Fissues%2F68)), moves **Shift Down** to the Left `–` Rocker (plus **Steering/Camera/Emote** on the Left D-Pad), and frees up **`Z` Single-Click** on the Right Pod for **Easy (4)**.

2. **Built-In Official `_openbikecontrol._tcp` Binary Server (Replaces BikeControl App Completely)**:
   - In [thies2005/AnkiMywhooshControl](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fthies2005%2FAnkiMywhooshControl), `OpenBikeControlClient.kt` failed because it acted as a TCP client sending custom JSON to port `21587`.
   - Per the official **[OpenBikeControl Protocol Specification (PROTOCOL.md \& MDNS.md)](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fopenbikecontrol-protocol)**, **your app acts as the TCP Server + mDNS Advertiser (`_openbikecontrol._tcp`)**!
   - When you open MyWhoosh (either on the **same Android Tablet** or on your **Pixel 10 over Wi-Fi**) and tap the **OpenBikeControl icon** in the top-right of MyWhoosh's Connection Screen ([MyWhoosh Virtual Shifting Guide](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fblog%2Fmywhoosh-virtual-shifting-guide%2F)), MyWhoosh discovers your Tablet via mDNS, connects to your app's `ServerSocket`, and receives native binary `0x01` button-state opcodes (`0x01` = Shift Up, `0x02` = Shift Down, `0x18` = Steer Left, `0x19` = Steer Right). **You don't even need this app installed on your Pixel 10 when running MyWhoosh on the Pixel 10!** (And a local `dispatchGesture` screen-tap fallback for [MyWhoosh MyShift](https://www.google.com/search?q=https%3A%2F%2Fmywhoosh.com%2Fmyshift-mywhooshs-virtual-shifting-explained%2F) is also included if you ever ride offline without Wi-Fi).

3. **Zero-Quirk 3-Layer AnkiDroid Clicking Engine + 350ms Bounce Guard**:
   - **Quirk Eliminated #1 (Split-Screen Focus Stealing)**: Uses `flagRetrieveInteractiveWindows` + `service.windows` ([Foreign App Navigation with Accessibility Service](https://www.google.com/search?q=https%3A%2F%2Fwww.noser.com%2Ftechblog%2Fforeign-app-navigation-with-accessibility-service%2F)) so your app clicks [AnkiDroid](https://www.google.com/search?q=https%3A%2F%2Fdocs.ankidroid.org%2F) in one split-screen pane while MyWhoosh keeps active touch focus in the other pane.
   - **Quirk Eliminated #2 (Accidental Double-Press Grading on Bumpy Sprints)**: Enforces a **350ms post-flip guard (`FLIP_TO_GRADE_GUARD_MS = 350L`)** after clicking **Show Answer**. If your thumb double-bounces while flipping a card, the second bounce is ignored so you never accidentally grade a card before reading the answer.
   - **Quirk Eliminated #3 (New Reviewer UI vs. Classic Reviewer Compatibility)**: Combines **Layer 1** (exact resource ID lookup for both [AnkiDroid's New Reviewer UI](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fankidroid%2FAnki-Android%2Fissues%2F18575) and Classic Reviewer), **Layer 2** (bottom-bar clickable label/contentDescription matching), and **Layer 3** (window-relative geometric `dispatchGesture` tap on AnkiDroid's bottom answer bar at `y = 93%`, `x = 12.5% / 37.5% / 62.5% / 87.5%`).

## 2. Complete Button Mapping Proposal: 1-Pod Default vs. 2-Pod Full Mode

| Physical Button | Bitmask (`0x23` Inverse) | Gesture | **1-Pod Default (`rightPodOnlyNoZwift = true`)** | **2-Pod Full Mode (`rightPodOnlyNoZwift = false`)** | **Anki-Only Mode (`MODE_3`)** | **MyWhoosh-Only Mode (`MODE_4`)** |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Right `+` Rocker** | `0x2000` | Single Click | **MyWhoosh: Shift Up (`0x01`)** | **MyWhoosh: Shift Up (`0x01`)** | Anki: Flip / Good (`3`) | MyWhoosh: Shift Up (`0x01`) |
| **Right `+` Rocker** | `0x2000` | Long Press (450ms) | **MyWhoosh: Shift Down (`0x02`)** | **MyWhoosh: Shift Up (+2)** | Anki: Undo | MyWhoosh: Shift Down (`0x02`) |
| **Right `Z` (Top)** | `0x0100` | Single Click | **MyWhoosh: Shift Down (`0x02`)** | **Anki: Flip / Easy (`4`)** | Anki: Flip / Easy (`4`) | MyWhoosh: Shift Down (`0x02`) |
| **Right `Z` (Top)** | `0x0100` | Long Press (450ms) | **Anki: Undo** | **Anki: Undo** | Anki: Undo | MyWhoosh: Screenshot (`0x24`) |
| **Right `A` (Bottom)** | `0x0010` | Single Click | **Anki: Flip / Good (`3`)** | **Anki: Flip / Good (`3`)** | Anki: Flip / Good (`3`) | MyWhoosh: Select (`0x14`) |
| **Right `A` (Bottom)** | `0x0010` | Long Press (450ms) | **Anki: Flip / Easy (`4`)** | **Anki: Flip / Easy (`4`)** | Anki: Flip / Easy (`4`) | MyWhoosh:Power-up (`0x50`) |
| **Right `B` (Right)** | `0x0020` | Single Click | **Anki: Flip / Hard (`2`)** | **Anki: Flip / Hard (`2`)** | Anki: Flip / Hard (`2`) | MyWhoosh: Back (`0x15`) |
| **Right `Y` (Left)** | `0x0040` | Single Click | **Anki: Flip / Again (`1`)** | **Anki: Flip / Again (`1`)** | Anki: Flip / Again (`1`) | MyWhoosh: HUD Toggle (`0x44`) |
| **Right `Y` (Left)** | `0x0040` | Long Press (450ms) | **Anki: Undo** | **Anki: Undo** | Anki: Undo | MyWhoosh: Emote (`0x20`) |
| **Left `–` Rocker** | `0x0400` | Single Click | *(Ignored in 1-Pod Mode)* | **MyWhoosh: Shift Down (`0x02`)** | Anki: Undo | MyWhoosh: Shift Down (`0x02`) |
| **Left D-Pad `◀` / `▶`** | `0x0001` / `0x0004` | Single / Hold | *(Ignored in 1-Pod Mode)* | **MyWhoosh: Steer Left / Right (`0x18`/`0x19`)** | Anki: Flip Card | MyWhoosh: Steer Left / Right |
| **Left D-Pad `▲` / `▼`** | `0x0002` / `0x0008` | Single Click | *(Ignored in 1-Pod Mode)* | **MyWhoosh: Camera (`0x40`) / Emote (`0x20`)** | Anki: Undo | MyWhoosh: Camera / Emote |

## 3. Parallel Subagent Remediation Matrix for `thies2005/AnkiMywhooshControl`

| Wave | Subagent ID | Owned Repository Paths to Overwrite | Blocking Security Gate |
| :--- | :--- | :--- | :--- |
| **Wave 0** | `ORCHESTRATOR` | `app/src/main/java/com/ankiride/bridge/core/Contracts.kt`, `verify.sh` | `SG-0` |
| **Wave 1A** | `SA-1-BLE` | `app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt`, `ZwiftGestureEngine.kt`, `ZwiftBleController.kt`, and `app/src/test/java/com/ankiride/bridge/ble/*` | `SG-1` |
| **Wave 1B** | `SA-2-ANKI` | `app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt`, `AnkiActionDispatcherImpl.kt`, `AnkiContentProviderHelper.kt`, `app/src/main/res/xml/accessibility_service_config.xml`, and `app/src/test/java/com/ankiride/bridge/anki/*` | `SG-2` & `SG-3` |
| **Wave 1C** | `SA-3-MYWHOOSH` | `app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt`, `MyShiftGestureFallback.kt`, `OpenBikeControlClient.kt`, `MyWhooshNetworkDispatcherImpl.kt`, `MyWhooshDiscovery.kt`, `app/src/main/res/xml/network_security_config.xml`, and `app/src/test/java/com/ankiride/bridge/mywhoosh/*` | `SG-4` |
| **Wave 1D** | `SA-4-APP-UI` | `app/src/main/java/com/ankiride/bridge/service/ActionRouter.kt`, `BridgeAccessibilityService.kt`, `BridgeForegroundService.kt`, `app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt`, `app/src/main/AndroidManifest.xml`, and `app/src/test/java/com/ankiride/bridge/service/*` | `SG-3` |

## 4. Wave 0 Drop-In Replacement: `core/Contracts.kt`

```kotlin
package com.ankiride.bridge.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class OperatingMode {
    MODE_1_TABLET_SPLIT_SCREEN,
    MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
    MODE_3_ANKI_ONLY,
    MODE_4_MYWHOOSH_ONLY
}

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    HANDSHAKING,
    CONNECTED_RIGHT_ONLY,
    CONNECTED_BOTH_PODS,
    ERROR
}

enum class ClickButton(val bitmask: Int) {
    LEFT(0x0001),
    UP(0x0002),
    RIGHT(0x0004),
    DOWN(0x0008),
    A(0x0010),
    B(0x0020),
    Y(0x0040),
    Z(0x0100),
    SHIFT_UP_LEFT(0x0200),
    SHIFT_DOWN_LEFT(0x0400),
    POWERUP_LEFT(0x0800),
    ONOFF_LEFT(0x1000),
    SHIFT_UP_RIGHT(0x2000),
    SHIFT_DOWN_RIGHT(0x4000),
    POWERUP_RIGHT(0x10000),
    ONOFF_RIGHT(0x20000)
}

enum class PressType {
    SINGLE_CLICK,
    LONG_PRESS
}

data class ButtonGestureEvent(
    val button: ClickButton,
    val type: PressType,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class AnkiAction {
    FLIP_OR_AGAIN,
    FLIP_OR_HARD,
    FLIP_OR_GOOD,
    FLIP_OR_EASY,
    FLIP_CARD,
    UNDO
}

enum class MyWhooshAction(val openBikeOpcode: Byte) {
    SHIFT_UP(0x01),
    SHIFT_DOWN(0x02),
    UI_NAV_UP(0x10),
    UI_NAV_DOWN(0x11),
    UI_NAV_LEFT(0x12),
    UI_NAV_RIGHT(0x13),
    UI_CONFIRM(0x14),
    UI_BACK(0x15),
    PAUSE_MENU(0x16),
    STEER_LEFT(0x18),
    STEER_RIGHT(0x19),
    EMOTE(0x20),
    SCREENSHOT(0x24),
    TOGGLE_CAMERA(0x40),
    HUD_TOGGLE(0x44),
    USE_POWERUP(0x50)
}

data class AppConfiguration(
    val mode: OperatingMode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
    val rightPodOnlyNoZwift: Boolean = true,
    val useZForShiftDownInSinglePodMode: Boolean = true,
    val leftPodAutoReconnectSec: Int = 55,
    val openBikeServerPort: Int = 21587,
    val useMyShiftTouchFallbackWhenNoClient: Boolean = true,
    val myShiftUpNormalizedX: Float = 0.92f,
    val myShiftUpNormalizedY: Float = 0.85f,
    val myShiftDownNormalizedX: Float = 0.84f,
    val myShiftDownNormalizedY: Float = 0.85f,
    val pinnedRightPodMac: String? = null,
    val pinnedLeftPodMac: String? = null
)

interface ClickBleController {
    val connectionState: StateFlow<BleConnectionState>
    val gestureEvents: SharedFlow<ButtonGestureEvent>
    fun startScanAndConnect(config: AppConfiguration)
    fun disconnect()
}

interface AnkiActionDispatcher {
    fun dispatch(action: AnkiAction): Boolean
}

interface MyWhooshActionDispatcher {
    suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean
}
```

## 5. Wave 1A (`SA-1-BLE`) Drop-In Code: BLE Dual/Single Pod Engine

### 5.1 `ble/ZwiftPacketParser.kt`

Decodes `0x23` protobuf messages with strict `SG-1` bounds (`3..64` bytes, max `5` varint bytes) and `.inv()` bit inversion ([Makinolo Zwift Protocol](https://www.google.com/search?q=https%3A%2F%2Fwww.makinolo.com%2Fblog%2F2024%2F07%2F26%2Fzwift-ride-protocol%2F)):

```kotlin
package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ClickButton
import java.util.EnumMap

object ZwiftPacketParser {
    const val MSG_KEYPAD_STATUS: Byte = 0x23
    const val PROTO_FIELD_1_VARINT: Byte = 0x08
    const val MAX_PACKET_SIZE = 64
    const val MAX_VARINT_BYTES = 5

    fun parseActiveButtonMask(payload: ByteArray): Int? {
        if (payload.size < 3 || payload.size > MAX_PACKET_SIZE) return null
        if (payload[0] != MSG_KEYPAD_STATUS || payload[1] != PROTO_FIELD_1_VARINT) return null

        var result = 0
        var shift = 0
        var index = 2
        var bytesRead = 0

        while (index < payload.size) {
            if (bytesRead >= MAX_VARINT_BYTES) return null
            val b = payload[index].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            bytesRead++
            index++
            if ((b and 0x80) == 0) {
                return result.inv()
            }
            shift += 7
        }
        return null
    }

    fun parse(data: ByteArray): Map<ClickButton, Boolean> {
        val result = EnumMap<ClickButton, Boolean>(ClickButton::class.java)
        val mask = parseActiveButtonMask(data)
        if (mask == null) {
            ClickButton.entries.forEach { result[it] = false }
            return result
        }
        ClickButton.entries.forEach { button ->
            result[button] = (mask and button.bitmask) != 0
        }
        return result
    }
}
```

### 5.2 `ble/ZwiftGestureEngine.kt`

Zero-quirk gesture detector: emits `SINGLE_CLICK` on release (`< 450ms`) and `LONG_PRESS` once held for `450ms`, never double-firing both events on a single press:

```kotlin
package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickButton
import com.ankiride.bridge.core.PressType
import java.util.EnumMap

class ZwiftGestureEngine(
    private val onGestureDetected: (ButtonGestureEvent) -> Unit
) {
    companion object {
        const val LONG_PRESS_THRESHOLD_MS = 450L
    }

    private class ButtonStateTracker {
        var isPressed: Boolean = false
        var pressStartTime: Long = 0L
        var longPressTriggered: Boolean = false
    }

    private val trackers = EnumMap<ClickButton, ButtonStateTracker>(ClickButton::class.java).apply {
        ClickButton.entries.forEach { put(it, ButtonStateTracker()) }
    }

    fun processButtonStates(states: Map<ClickButton, Boolean>, nowMs: Long = System.currentTimeMillis()) {
        states.forEach { (button, isPressed) ->
            val tracker = trackers[button] ?: return@forEach

            if (isPressed && !tracker.isPressed) {
                tracker.isPressed = true
                tracker.pressStartTime = nowMs
                tracker.longPressTriggered = false
            } else if (isPressed && tracker.isPressed) {
                if (!tracker.longPressTriggered && (nowMs - tracker.pressStartTime) >= LONG_PRESS_THRESHOLD_MS) {
                    tracker.longPressTriggered = true
                    onGestureDetected(ButtonGestureEvent(button, PressType.LONG_PRESS, nowMs))
                }
            } else if (!isPressed && tracker.isPressed) {
                tracker.isPressed = false
                val holdDuration = nowMs - tracker.pressStartTime
                if (!tracker.longPressTriggered && holdDuration < LONG_PRESS_THRESHOLD_MS) {
                    onGestureDetected(ButtonGestureEvent(button, PressType.SINGLE_CLICK, nowMs))
                }
            }
        }
    }
}
```

### 5.3 `ble/ZwiftBleController.kt`

Defaults to **1-Controller Mode (`rightPodOnlyNoZwift = true`)** while supporting **2-Controller Mode (`rightPodOnlyNoZwift = false`)** with separate Left/Right GATT tracking and the 55-second Left-Pod watchdog ([OpenBikeControl Issue #68](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fbikecontrol%2Fissues%2F68)):

```kotlin
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
```

## 6. Wave 1B (`SA-2-ANKI`) Drop-In Code: 3-Layer Zero-Quirk AnkiDroid Clicking Engine

### 6.1 `anki/AnkiAccessibilityBridge.kt`

Implements **3-Layer Button Resolution** (View ID $ightarrow$ Bottom-Bar Text/ContentDescription $ightarrow$ Window-Relative Geometric Bottom-Bar Tap) plus a **350ms Post-Flip Bounce Guard** (`FLIP_TO_GRADE_GUARD_MS = 350L`) so you never accidentally grade a card on a double-bounce:

```kotlin
package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ankiride.bridge.core.AnkiAction

class AnkiAccessibilityBridge {

    companion object {
        const val ANKI_PACKAGE = "com.ichi2.anki"
        const val FLIP_TO_GRADE_GUARD_MS = 350L

        private val SHOW_ANSWER_IDS = listOf(
            "com.ichi2.anki:id/show_answer",
            "com.ichi2.anki:id/flashcard_layout_flip",
            "com.ichi2.anki:id/answer_options_layout"
        )
        private val EASE_1_IDS = listOf("com.ichi2.anki:id/again_button", "com.ichi2.anki:id/flashcard_layout_ease1")
        private val EASE_2_IDS = listOf("com.ichi2.anki:id/hard_button", "com.ichi2.anki:id/flashcard_layout_ease2")
        private val EASE_3_IDS = listOf("com.ichi2.anki:id/good_button", "com.ichi2.anki:id/flashcard_layout_ease3")
        private val EASE_4_IDS = listOf("com.ichi2.anki:id/easy_button", "com.ichi2.anki:id/flashcard_layout_ease4")
        private val UNDO_IDS = listOf("com.ichi2.anki:id/action_undo")
    }

    @Volatile
    private var lastFlipTimestampMs: Long = 0L

    fun performAnkiAction(service: AccessibilityService, action: AnkiAction, nowMs: Long = System.currentTimeMillis()): Boolean {
        val ankiWindow: AccessibilityWindowInfo? = service.windows
            ?.firstOrNull { it.root?.packageName?.toString() == ANKI_PACKAGE }

        val ankiRoot: AccessibilityNodeInfo? = ankiWindow?.root
            ?: service.rootInActiveWindow?.takeIf { it.packageName?.toString() == ANKI_PACKAGE }

        if (ankiRoot != null) {
            val handledByNode = findAndClickButton(ankiRoot, action, nowMs)
            if (handledByNode) return true
        }

        // Layer 3 Fallback: Window-relative geometric tap on AnkiDroid's bottom answer bar
        if (ankiWindow != null && action != AnkiAction.UNDO) {
            if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) return false
            val bounds = Rect()
            ankiWindow.getBoundsInScreen(bounds)
            return dispatchBottomBarColumnTap(service, bounds, action)
        }
        return false
    }

    fun findAndClickButton(
        root: AccessibilityNodeInfo?,
        action: AnkiAction,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (root == null || root.packageName?.toString() != ANKI_PACKAGE) return false

        return when (action) {
            AnkiAction.FLIP_CARD -> {
                val flipped = clickByIdsOrText(root, SHOW_ANSWER_IDS, listOf("Show answer", "Show Answer"))
                if (flipped) lastFlipTimestampMs = nowMs
                flipped
            }
            AnkiAction.FLIP_OR_AGAIN -> clickShowAnswerOrEase(root, EASE_1_IDS, listOf("Again"), nowMs)
            AnkiAction.FLIP_OR_HARD -> clickShowAnswerOrEase(root, EASE_2_IDS, listOf("Hard", "Again"), nowMs)
            AnkiAction.FLIP_OR_GOOD -> clickShowAnswerOrEase(root, EASE_3_IDS, listOf("Good"), nowMs)
            AnkiAction.FLIP_OR_EASY -> clickShowAnswerOrEase(root, EASE_4_IDS, listOf("Easy", "Good"), nowMs)
            AnkiAction.UNDO -> clickByIdsOrText(root, UNDO_IDS, listOf("Undo"))
        }
    }

    private fun clickShowAnswerOrEase(
        root: AccessibilityNodeInfo,
        easeIds: List<String>,
        easeTexts: List<String>,
        nowMs: Long
    ): Boolean {
        // Step 1: If "Show Answer" is visible, flip the card and arm the 350ms bounce guard
        if (clickByIdsOrText(root, SHOW_ANSWER_IDS, listOf("Show answer", "Show Answer"))) {
            lastFlipTimestampMs = nowMs
            return true
        }
        // Step 2: Enforce 350ms post-flip guard so a double-bounce never grades the card unread
        if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) {
            return false
        }
        // Step 3: Click the target Ease button (Again / Hard / Good / Easy)
        return clickByIdsOrText(root, easeIds, easeTexts)
    }

    private fun clickByIdsOrText(
        root: AccessibilityNodeInfo,
        viewIds: List<String>,
        fallbackTexts: List<String>
    ): Boolean {
        for (id in viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            for (node in nodes) {
                if (!node.isPassword && node.isVisibleToUser && clickNodeOrParent(node)) {
                    return true
                }
            }
        }
        for (text in fallbackTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text) ?: continue
            for (node in nodes) {
                if (!node.isPassword && node.isVisibleToUser && clickNodeOrParent(node)) {
                    return true
                }
            }
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isPassword) return false
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun dispatchBottomBarColumnTap(
        service: AccessibilityService,
        windowBounds: Rect,
        action: AnkiAction
    ): Boolean {
        if (windowBounds.width() <= 0 || windowBounds.height() <= 0) return false
        val colNormX = when (action) {
            AnkiAction.FLIP_OR_AGAIN -> 0.125f
            AnkiAction.FLIP_OR_HARD -> 0.375f
            AnkiAction.FLIP_OR_GOOD, AnkiAction.FLIP_CARD -> 0.625f
            AnkiAction.FLIP_OR_EASY -> 0.875f
            AnkiAction.UNDO -> return false
        }
        val tapX = windowBounds.left + (windowBounds.width() * colNormX)
        val tapY = windowBounds.top + (windowBounds.height() * 0.93f)

        val path = Path().apply { moveTo(tapX, tapY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 15L)
        return service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
```

## 7. Wave 1C (`SA-3-MYWHOOSH`) Drop-In Code: Standalone `_openbikecontrol._tcp` Binary Server + Local Touch Fallback

### 7.1 `mywhoosh/OpenBikeControlClient.kt` (Upgraded to Official `_openbikecontrol._tcp` Server + mDNS Advertiser)

Implements the exact binary wire specification from [OpenBikeControl/openbikecontrol-protocol](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fopenbikecontrol-protocol) (`0x01` Button State `[0x01, opcode, 0x01]` followed by release `[0x01, opcode, 0x00]`, plus `0x02` Device Status `[0x02, 0x64, 0x01]` and `_openbikecontrol._tcp` mDNS registration via Android `NsdManager`). When you tap the **OpenBikeControl** icon in MyWhoosh's Connection Screen (on either your Tablet or your Pixel 10), MyWhoosh connects directly to this server:

```kotlin
package com.ankiride.bridge.mywhoosh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.ankiride.bridge.core.MyWhooshAction
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class OpenBikeControlClient(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        const val SERVICE_TYPE = "_openbikecontrol._tcp."
        const val SERVICE_NAME = "AnkiRideBridge"
        const val MSG_BUTTON_STATE: Byte = 0x01
        const val MSG_DEVICE_STATUS: Byte = 0x02
        const val STATE_PRESSED: Byte = 0x01
        const val STATE_RELEASED: Byte = 0x00
    }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private val connectedClients = CopyOnWriteArrayList<Socket>()

    fun startServer(port: Int = 21587) {
        if (serverSocket != null) return
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val srv = ServerSocket(port)
                serverSocket = srv
                registerMdnsService(srv.localPort)
                while (isActive && !srv.isClosed) {
                    val client = srv.accept()
                    val remoteHost = client.inetAddress?.hostAddress ?: ""
                    if (LanAddressValidator.isLocalOrLanAddress(remoteHost)) {
                        connectedClients.add(client)
                        // Send initial OpenBikeControl 0x02 Device Status (100% battery, connected flag = 1)
                        sendRawBytes(client, byteArrayOf(MSG_DEVICE_STATUS, 0x64, 0x01))
                    } else {
                        client.close()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun hasConnectedClients(): Boolean = connectedClients.any { !it.isClosed && it.isConnected }

    suspend fun sendAction(ip: String, port: Int, action: MyWhooshAction): Boolean = withContext(Dispatchers.IO) {
        if (!LanAddressValidator.isLocalOrLanAddress(ip)) return@withContext false

        val pressPacket = byteArrayOf(MSG_BUTTON_STATE, action.openBikeOpcode, STATE_PRESSED)
        val releasePacket = byteArrayOf(MSG_BUTTON_STATE, action.openBikeOpcode, STATE_RELEASED)

        var delivered = false
        for (client in connectedClients) {
            if (client.isClosed || !client.isConnected) {
                connectedClients.remove(client)
                continue
            }
            if (sendRawBytes(client, pressPacket)) {
                delivered = true
            } else {
                connectedClients.remove(client)
            }
        }
        if (delivered) {
            delay(35L)
            for (client in connectedClients) {
                sendRawBytes(client, releasePacket)
            }
        }
        delivered
    }

    private fun sendRawBytes(socket: Socket, bytes: ByteArray): Boolean {
        return try {
            val out: OutputStream = socket.getOutputStream()
            synchronized(out) {
                out.write(bytes)
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun registerMdnsService(port: Int) {
        val ctx = context ?: return
        val manager = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("v", "1")
            setAttribute("version", "1")
            setAttribute("name", "AnkiRide Bridge")
            setAttribute("model", "Zwift Click v2 Bridge")
            setAttribute("proto", "tcp")
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {}
            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    fun stopServer() {
        acceptJob?.cancel()
        registrationListener?.let { listener ->
            try {
                nsdManager?.unregisterService(listener)
            } catch (_: Exception) {
            }
        }
        registrationListener = null
        connectedClients.forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        connectedClients.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }
}
```

### 7.2 `mywhoosh/MyWhooshNetworkDispatcherImpl.kt`

Automatically uses the standalone `_openbikecontrol._tcp` binary connection whenever MyWhoosh is connected (on the Tablet or Pixel 10), and seamlessly falls back to local `MyShiftGestureFallback` screen taps if you ride on the Tablet without tapping the OpenBikeControl icon:

```kotlin
package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.MyWhooshAction
import com.ankiride.bridge.core.MyWhooshActionDispatcher
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.service.BridgeAccessibilityService

class MyWhooshNetworkDispatcherImpl(
    private val client: OpenBikeControlClient,
    private val accessibilityServiceSupplier: () -> BridgeAccessibilityService?
) : MyWhooshActionDispatcher {

    override suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean {
        // 1. Primary: Send official binary OpenBikeControl packet (0x01) to connected MyWhoosh client (Tablet or Pixel 10)
        if (client.hasConnectedClients()) {
            if (client.sendAction("127.0.0.1", config.openBikeServerPort, action)) {
                return true
            }
        }

        // 2. Fallback: If MyWhoosh is on the Tablet and OpenBikeControl isn't paired yet, tap MyShift arrows directly
        val isTabletMyWhoosh = config.mode == OperatingMode.MODE_1_TABLET_SPLIT_SCREEN ||
            config.mode == OperatingMode.MODE_4_MYWHOOSH_ONLY

        if (isTabletMyWhoosh && config.useMyShiftTouchFallbackWhenNoClient) {
            val service = accessibilityServiceSupplier()
            if (service != null && MyShiftGestureFallback.performTouchAction(service, action, config)) {
                return true
            }
        }
        return false
    }
}
```

## 8. Wave 1D (`SA-4-APP-UI`) Drop-In Code: 1-Pod Default / 2-Pod Full `ActionRouter` & UI

### 8.1 `service/ActionRouter.kt`

Implements the exact 1-Controller Default (`rightPodOnlyNoZwift = true`, where **Top `+` = Shift Up**, **Top `Z` = Shift Down**, and **`Y / B / A` = Again / Hard / Good** with **Long-Press `A` = Easy** and **Long-Press `Y` = Undo**) AND full 2-Controller Mode (`rightPodOnlyNoZwift = false`, where **Left `–` = Shift Down**, **Left D-Pad = Steer/Camera/Emote**, and **`Y / B / A / Z` = Again / Hard / Good / Easy**):

```kotlin
package com.ankiride.bridge.service

import com.ankiride.bridge.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ActionRouter(
    private val scope: CoroutineScope,
    private val configSupplier: () -> AppConfiguration,
    private val ankiDispatcher: AnkiActionDispatcher,
    private val myWhooshDispatcher: MyWhooshActionDispatcher
) {

    fun routeGesture(event: ButtonGestureEvent) {
        val config = configSupplier()

        when (config.mode) {
            OperatingMode.MODE_3_ANKI_ONLY -> {
                val ankiAction = mapAnkiOnlyMode(event) ?: return
                ankiDispatcher.dispatch(ankiAction)
            }
            OperatingMode.MODE_4_MYWHOOSH_ONLY -> {
                val mwAction = mapMyWhooshOnlyMode(event, config) ?: return
                scope.launch { myWhooshDispatcher.dispatch(mwAction, config) }
            }
            OperatingMode.MODE_1_TABLET_SPLIT_SCREEN, OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH -> {
                if (config.rightPodOnlyNoZwift) {
                    routeSinglePodCombinedMode(event, config)
                } else {
                    routeDualPodCombinedMode(event, config)
                }
            }
        }
    }

    private fun routeSinglePodCombinedMode(event: ButtonGestureEvent, config: AppConfiguration) {
        when (event.button) {
            ClickButton.SHIFT_UP_RIGHT -> {
                val mwAction = if (event.type == PressType.LONG_PRESS) {
                    MyWhooshAction.SHIFT_DOWN
                } else {
                    MyWhooshAction.SHIFT_UP
                }
                scope.launch { myWhooshDispatcher.dispatch(mwAction, config) }
            }
            ClickButton.Z -> {
                if (event.type == PressType.LONG_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.UNDO)
                } else if (config.useZForShiftDownInSinglePodMode) {
                    scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
                } else {
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASY)
                }
            }
            ClickButton.A -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.FLIP_OR_EASY else AnkiAction.FLIP_OR_GOOD
                ankiDispatcher.dispatch(action)
            }
            ClickButton.B -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            ClickButton.Y -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_AGAIN
                ankiDispatcher.dispatch(action)
            }
            else -> {}
        }
    }

    private fun routeDualPodCombinedMode(event: ButtonGestureEvent, config: AppConfiguration) {
        when (event.button) {
            // Right Pod A/B/Y/Z -> AnkiDroid
            ClickButton.A -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_GOOD)
            ClickButton.B -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            ClickButton.Y -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_AGAIN)
            ClickButton.Z -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_EASY
                ankiDispatcher.dispatch(action)
            }
            // Shift Rockers & Left Pod D-Pad -> MyWhoosh
            ClickButton.SHIFT_UP_RIGHT, ClickButton.SHIFT_UP_LEFT -> {
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_UP, config) }
            }
            ClickButton.SHIFT_DOWN_LEFT, ClickButton.SHIFT_DOWN_RIGHT -> {
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
            }
            ClickButton.LEFT -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.STEER_LEFT, config) }
            ClickButton.RIGHT -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.STEER_RIGHT, config) }
            ClickButton.UP -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.TOGGLE_CAMERA, config) }
            ClickButton.DOWN -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.EMOTE, config) }
            else -> {}
        }
    }

    private fun mapAnkiOnlyMode(event: ButtonGestureEvent): AnkiAction? {
        return when (event.button) {
            ClickButton.A, ClickButton.SHIFT_UP_RIGHT -> AnkiAction.FLIP_OR_GOOD
            ClickButton.B -> AnkiAction.FLIP_OR_HARD
            ClickButton.Y -> if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_AGAIN
            ClickButton.Z -> if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_EASY
            ClickButton.SHIFT_DOWN_LEFT, ClickButton.LEFT -> AnkiAction.UNDO
            ClickButton.RIGHT, ClickButton.UP, ClickButton.DOWN -> AnkiAction.FLIP_CARD
            else -> null
        }
    }

    private fun mapMyWhooshOnlyMode(event: ButtonGestureEvent, config: AppConfiguration): MyWhooshAction? {
        return when (event.button) {
            ClickButton.SHIFT_UP_RIGHT, ClickButton.SHIFT_UP_LEFT -> {
                if (config.rightPodOnlyNoZwift && event.type == PressType.LONG_PRESS) MyWhooshAction.SHIFT_DOWN
                else MyWhooshAction.SHIFT_UP
            }
            ClickButton.SHIFT_DOWN_LEFT, ClickButton.SHIFT_DOWN_RIGHT -> MyWhooshAction.SHIFT_DOWN
            ClickButton.Z -> if (config.rightPodOnlyNoZwift) MyWhooshAction.SHIFT_DOWN else MyWhooshAction.USE_POWERUP
            ClickButton.LEFT -> MyWhooshAction.STEER_LEFT
            ClickButton.RIGHT -> MyWhooshAction.STEER_RIGHT
            ClickButton.UP -> MyWhooshAction.TOGGLE_CAMERA
            ClickButton.DOWN -> MyWhooshAction.EMOTE
            ClickButton.A -> MyWhooshAction.UI_CONFIRM
            ClickButton.B -> MyWhooshAction.UI_BACK
            ClickButton.Y -> MyWhooshAction.HUD_TOGGLE
            else -> null
        }
    }
}
```

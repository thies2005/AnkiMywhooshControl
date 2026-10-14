# Multi-Agent Phase Plan & Review-Gated Specification: `AnkiMywhooshControl` v2.0

## 1. Executive Summary & Root-Cause Fixes Included in This Plan

This multi-agent specification upgrades **[thies2005/AnkiMywhooshControl](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fopenbikecontrol-protocol)** (`com.ankiride.bridge`) to resolve every UI, lifecycle, networking, and diagnostic issue identified during tablet testing:

1. **100% Free & Standalone — Zero Reliance on the Paid BikeControl App**:
   - The app implements the open, MIT-licensed **[OpenBikeControl Network Specification (\_openbikecontrol.\_tcp)](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fopenbikecontrol-protocol)** directly inside `OpenBikeControlClient.kt` as a local TCP Server (`port 21587`) + Android `NsdManager` mDNS Advertiser.
   - **[MyWhoosh](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fblog%2Fmywhoosh-virtual-shifting-guide%2F)** natively supports `_openbikecontrol._tcp` built into the game client (via the OpenBikeControl icon in the top-right of MyWhoosh's Connection Screen). You **never** need the paid BikeControl app or the Zwift app installed on your Tablet or Pixel 10.
   - Includes a crucial fix in `LanAddressValidator.kt` to normalize Android dual-stack IPv4-mapped IPv6 socket addresses (`::ffff:127.0.0.1` and `::ffff:192.168.x.x`), which previously caused `ServerSocket.accept()` to reject local MyWhoosh connections on Android 14/15.

2. **Permissions & Battery Optimization Banner Auto-Refresh & Auto-Hide (`ON_RESUME` Fix)**:
   - **Root Cause in v1**: `PermissionsChecklistCard` in `MainActivity.kt` checked `BridgeAccessibilityService.activeInstance != null` and `powerManager.isIgnoringBatteryOptimizations(...)` as non-reactive local variables during initial composition, had no `Lifecycle.Event.ON_RESUME` observer, hardcoded `"Exempt"` in both branches of the Battery button (`if (batteryIgnored) "Exempt" else "Exempt"`), and hardcoded the BLE permission icon to green.
   - **Fix in v2**: Uses a `DisposableEffect` with `LifecycleEventObserver` on `Lifecycle.Event.ON_RESUME` + `BridgeAccessibilityService.isConnectedFlow` + `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` verification. The instant you accept Accessibility Service or Battery Optimization in Android Settings and return to the app, the statuses turn green—and once all 3 permissions are granted, the entire setup warning card **automatically collapses** into a single compact `"All System Permissions Granted"` badge.

3. **Zero Manual MAC Address Entry Required (Auto-Discovery by Default)**:
   - Removes the confusing raw `Right Pod MAC` / `Left Pod MAC` text boxes from the main dashboard.
   - The app automatically discovers your **[Zwift Click v2](https://www.google.com/search?q=https%3A%2F%2Fwww.makinolo.com%2Fblog%2F2024%2F07%2F26%2Fzwift-ride-protocol%2F)** via BLE Service UUIDs (`0xFC82` / `00000001-19ca-...`) and Manufacturer ID (`0x094A`), displays the connected controller's live MAC address on screen, and offers a one-tap `"Lock to Connected Controller"` toggle inside an optional collapsed Advanced section.

4. **In-App Live Diagnostic Logger with `Copy`, `Clear`, Filter Chips & Window Inspector**:
   - Adds `DiagnosticLogger` (`core/DiagnosticLogger.kt`), a thread-safe 500-entry ring buffer exposed via `StateFlow<List<LogEntry>>`.
   - Logs every BLE scan result, GATT handshake step (`RideOn`), raw `0x23` packet hex, decoded button press, router action, AnkiDroid window/node match (Layer 1 View ID vs. Layer 2 Text vs. Layer 3 Geometric Tap vs. 350ms Bounce Guard), and MyWhoosh TCP/touch dispatch.
   - Includes one-tap **Copy Logs** (copies formatted log history + current config to Android `ClipboardManager`), **Clear Logs**, category filter pills (`ALL`, `BLE`, `ANKI`, `MYWHOOSH`, `ERROR`), and an **Inspect Windows** button that dumps all active split-screen windows and visible AnkiDroid view IDs directly into the log.

5. **Custom Button Mapping UI + Quick Action Test Buttons**:
   - Adds interactive dropdowns/pickers in `MainActivity.kt` so you can customize the action assigned to any Zwift Click v2 button (`+ Rocker`, `Z`, `A`, `B`, `Y`, `– Rocker`, `D-Pad`) for both **Single Click** and **Long Press**, with a one-tap **"Reset to Recommended 1-Pod Default"** button and persistent storage via `SharedPreferences`.

---

## 2. Multi-Agent Phase Topology & Write-Isolation Matrix

To ensure weaker subagents never overwrite each other's files or break shared interfaces, execution is divided into **4 sequential/parallel phases** guarded by **6 deterministic Review & Security Gates (`RG-0` through `RG-5`)**.

| Phase | Subagent ID | Role Description | Owned Repository Files (Read-Write) | Read-Only Dependencies | Blocking Gate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Phase 0** | `ORCHESTRATOR` | Shared Contracts, `DiagnosticLogger` & Gate Script | `app/src/main/java/com/ankiride/bridge/core/Contracts.kt`, `app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt`, `verify.sh` | `build.gradle.kts`, `app/build.gradle.kts` | `RG-0` |
| **Phase 1A** | `SA-1-BLE` | BLE Scanner, Dual/Single GATT, `RideOn` & Packet Logger | `app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt`, `ZwiftGestureEngine.kt`, `ZwiftBleController.kt`, `app/src/test/java/com/ankiride/bridge/ble/*` | `core/*` | `RG-1` |
| **Phase 1B** | `SA-2-ANKI` | 3-Layer AnkiDroid Accessibility Engine + Window Dumper | `app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt`, `AnkiActionDispatcherImpl.kt`, `AnkiContentProviderHelper.kt`, `app/src/main/res/xml/accessibility_service_config.xml`, `app/src/test/java/com/ankiride/bridge/anki/*` | `core/*` | `RG-2` |
| **Phase 1C** | `SA-3-MYWHOOSH` | Standalone `_openbikecontrol._tcp` Server, Dual-Stack IP Fix & Touch Fallback | `app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt`, `OpenBikeControlClient.kt`, `MyShiftGestureFallback.kt`, `MyWhooshNetworkDispatcherImpl.kt`, `MyWhooshDiscovery.kt`, `app/src/main/res/xml/network_security_config.xml`, `app/src/test/java/com/ankiride/bridge/mywhoosh/*` | `core/*` | `RG-3` |
| **Phase 2** | `SA-4-APP-UI` | Custom Button Router, `ON_RESUME` Permission Observer, Live Log UI (`Copy`/`Clear`) | `app/src/main/java/com/ankiride/bridge/service/ActionRouter.kt`, `BridgeAccessibilityService.kt`, `BridgeForegroundService.kt`, `app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt`, `app/src/main/AndroidManifest.xml`, `app/src/test/java/com/ankiride/bridge/service/*` | `core/*`, `ble/*`, `anki/*`, `mywhoosh/*` | `RG-4` |
| **Phase 3** | `SA-5-REVIEWER` | Automated Gate Auditor & Unit Test Runner | Read-Only across entire repository (executes `./verify.sh` and `./gradlew testDebugUnitTest`) | Entire Repository | `RG-5` |

---

## 3. Phase 0 (`ORCHESTRATOR`): Shared Contracts & `DiagnosticLogger`

### 3.1 Instructions for `ORCHESTRATOR`
1. Overwrite `app/src/main/java/com/ankiride/bridge/core/Contracts.kt` and create `app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt` with the exact code below.
2. Run **Review Gate `RG-0`** before spawning Phase 1 subagents (`SA-1-BLE`, `SA-2-ANKI`, `SA-3-MYWHOOSH`).

### 3.2 `app/src/main/java/com/ankiride/bridge/core/Contracts.kt`

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

enum class ClickButton(val bitmask: Int, val displayName: String, val isRightPod: Boolean) {
    SHIFT_UP_RIGHT(0x2000, "Right Top '+' Rocker", true),
    Z(0x0100, "Right 'Z' (Top)", true),
    A(0x0010, "Right 'A' (Bottom)", true),
    B(0x0020, "Right 'B' (Right)", true),
    Y(0x0040, "Right 'Y' (Left)", true),
    SHIFT_DOWN_LEFT(0x0400, "Left Top '–' Rocker", false),
    LEFT(0x0001, "Left D-Pad ◀", false),
    UP(0x0002, "Left D-Pad ▲", false),
    RIGHT(0x0004, "Left D-Pad ▶", false),
    DOWN(0x0008, "Left D-Pad ▼", false),
    SHIFT_UP_LEFT(0x0200, "Left Aux Shift Up", false),
    POWERUP_LEFT(0x0800, "Left PowerUp", false),
    ONOFF_LEFT(0x1000, "Left On/Off", false),
    SHIFT_DOWN_RIGHT(0x4000, "Right Aux Shift Down", true),
    POWERUP_RIGHT(0x10000, "Right PowerUp", true),
    ONOFF_RIGHT(0x20000, "Right On/Off", true)
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

enum class AnkiAction(val displayName: String) {
    FLIP_OR_AGAIN("Anki: Flip / Again (1)"),
    FLIP_OR_HARD("Anki: Flip / Hard (2)"),
    FLIP_OR_GOOD("Anki: Flip / Good (3)"),
    FLIP_OR_EASY("Anki: Flip / Easy (4)"),
    FLIP_CARD("Anki: Flip Card Only"),
    UNDO("Anki: Undo Last Card")
}

enum class MyWhooshAction(val openBikeOpcode: Byte, val displayName: String) {
    SHIFT_UP(0x01, "MyWhoosh: Shift Up (+1)"),
    SHIFT_DOWN(0x02, "MyWhoosh: Shift Down (-1)"),
    UI_NAV_UP(0x10, "MyWhoosh: UI Up"),
    UI_NAV_DOWN(0x11, "MyWhoosh: UI Down"),
    UI_NAV_LEFT(0x12, "MyWhoosh: UI Left"),
    UI_NAV_RIGHT(0x13, "MyWhoosh: UI Right"),
    UI_CONFIRM(0x14, "MyWhoosh: Confirm / Select"),
    UI_BACK(0x15, "MyWhoosh: Back"),
    PAUSE_MENU(0x16, "MyWhoosh: Pause"),
    STEER_LEFT(0x18, "MyWhoosh: Steer Left"),
    STEER_RIGHT(0x19, "MyWhoosh: Steer Right"),
    EMOTE(0x20, "MyWhoosh: Emote"),
    SCREENSHOT(0x24, "MyWhoosh: Screenshot"),
    TOGGLE_CAMERA(0x40, "MyWhoosh: Camera Angle"),
    HUD_TOGGLE(0x44, "MyWhoosh: Toggle HUD"),
    USE_POWERUP(0x50, "MyWhoosh: Use Power-Up")
}

/**
 * Unified custom assignable action so the user can bind any button (Single or Long Press)
 * to any Anki or MyWhoosh command in the UI.
 */
enum class MappedTargetAction(
    val label: String,
    val ankiAction: AnkiAction? = null,
    val myWhooshAction: MyWhooshAction? = null
) {
    NONE("None (Disabled)"),
    MW_SHIFT_UP("MyWhoosh: Shift Up (+)", myWhooshAction = MyWhooshAction.SHIFT_UP),
    MW_SHIFT_DOWN("MyWhoosh: Shift Down (–)", myWhooshAction = MyWhooshAction.SHIFT_DOWN),
    MW_STEER_LEFT("MyWhoosh: Steer Left", myWhooshAction = MyWhooshAction.STEER_LEFT),
    MW_STEER_RIGHT("MyWhoosh: Steer Right", myWhooshAction = MyWhooshAction.STEER_RIGHT),
    MW_CAMERA("MyWhoosh: Toggle Camera", myWhooshAction = MyWhooshAction.TOGGLE_CAMERA),
    MW_EMOTE("MyWhoosh: Emote", myWhooshAction = MyWhooshAction.EMOTE),
    MW_HUD("MyWhoosh: Toggle HUD", myWhooshAction = MyWhooshAction.HUD_TOGGLE),
    MW_CONFIRM("MyWhoosh: UI Select", myWhooshAction = MyWhooshAction.UI_CONFIRM),
    MW_BACK("MyWhoosh: UI Back", myWhooshAction = MyWhooshAction.UI_BACK),
    MW_POWERUP("MyWhoosh: Power-Up", myWhooshAction = MyWhooshAction.USE_POWERUP),
    ANKI_FLIP_OR_AGAIN("Anki: Flip / Again (1)", ankiAction = AnkiAction.FLIP_OR_AGAIN),
    ANKI_FLIP_OR_HARD("Anki: Flip / Hard (2)", ankiAction = AnkiAction.FLIP_OR_HARD),
    ANKI_FLIP_OR_GOOD("Anki: Flip / Good (3)", ankiAction = AnkiAction.FLIP_OR_GOOD),
    ANKI_FLIP_OR_EASY("Anki: Flip / Easy (4)", ankiAction = AnkiAction.FLIP_OR_EASY),
    ANKI_FLIP_ONLY("Anki: Flip Card Only", ankiAction = AnkiAction.FLIP_CARD),
    ANKI_UNDO("Anki: Undo Last Card", ankiAction = AnkiAction.UNDO)
}

data class ButtonBindingKey(
    val button: ClickButton,
    val pressType: PressType
)

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
    val pinnedLeftPodMac: String? = null,
    val customBindings: Map<ButtonBindingKey, MappedTargetAction> = emptyMap()
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

### 3.3 `app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt`

```kotlin
package com.ankiride.bridge.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogCategory {
    SYSTEM,
    PERMS,
    BLE,
    PACKET,
    ROUTER,
    ANKI,
    MYWHOOSH
}

enum class LogLevel {
    INFO,
    WARN,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestampMs: Long,
    val formattedTime: String,
    val category: LogCategory,
    val level: LogLevel,
    val message: String
)

object DiagnosticLogger {
    const val MAX_LOG_ENTRIES = 500
    private var nextId = 1L
    private val lock = Any()
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(
        category: LogCategory,
        message: String,
        level: LogLevel = LogLevel.INFO,
        nowMs: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            val formatted = synchronized(timeFormatter) {
                timeFormatter.format(Date(nowMs))
            }
            val entry = LogEntry(
                id = nextId++,
                timestampMs = nowMs,
                formattedTime = formatted,
                category = category,
                level = level,
                message = message
            )
            val current = _logs.value
            val updated = if (current.size >= MAX_LOG_ENTRIES) {
                current.drop(current.size - MAX_LOG_ENTRIES + 1) + entry
            } else {
                current + entry
            }
            _logs.value = updated
        }
    }

    fun info(category: LogCategory, message: String) = log(category, message, LogLevel.INFO)
    fun warn(category: LogCategory, message: String) = log(category, message, LogLevel.WARN)
    fun error(category: LogCategory, message: String) = log(category, message, LogLevel.ERROR)

    fun clear() {
        synchronized(lock) {
            _logs.value = emptyList()
        }
    }

    fun formatForClipboard(filterCategory: LogCategory? = null): String {
        val snapshot = _logs.value.let { list ->
            if (filterCategory == null) list else list.filter { it.category == filterCategory }
        }
        if (snapshot.isEmpty()) return "[AnkiRide Bridge Log Empty]"
        val sb = StringBuilder()
        sb.appendLine("=== AnkiRide Bridge Diagnostic Log (${snapshot.size} entries) ===")
        for (item in snapshot) {
            sb.append('[')
                .append(item.formattedTime)
                .append("] [")
                .append(item.level.name)
                .append("] [")
                .append(item.category.name)
                .append("] ")
                .appendLine(item.message)
        }
        return sb.toString()
    }
}
```

---

## 4. Phase 1A (`SA-1-BLE`): BLE Controller with Full Diagnostic Logging & Dual Write-Type Handshake

### 4.1 Instructions for `SA-1-BLE`
- **Assigned Scope**: `app/src/main/java/com/ankiride/bridge/ble/**` and `app/src/test/java/com/ankiride/bridge/ble/**`.
- Keep `ZwiftPacketParser.kt` and `ZwiftGestureEngine.kt` compliant with `SG-1` (`MAX_PACKET_SIZE = 64`, `MAX_VARINT_BYTES = 5`, `.inv()` bitmask logic, `LONG_PRESS_THRESHOLD_MS = 450L`).
- Upgrade `ZwiftBleController.kt` to:
  1. Log every scan start, discovered Zwift Click v2 pod (`MAC`, `RSSI`, manufacturer data hex, Right `0x07` vs Left `0x08` vs Click `0x09`), GATT connection state change, service discovery result, CCCD descriptor write, `"RideOn"` handshake write, and decoded button state transition via `DiagnosticLogger`.
  2. Expose `lastConnectedRightMac: StateFlow<String?>` and `lastConnectedLeftMac: StateFlow<String?>` so the UI can show the user their connected controller's MAC address and let them lock/unlock it with one tap without ever typing a MAC address manually.
  3. Automatically select `WRITE_TYPE_NO_RESPONSE` or `WRITE_TYPE_DEFAULT` based on `controlChar.properties` when writing `"RideOn"` so all Zwift Click v2 firmware revisions complete the handshake cleanly.

### 4.2 `app/src/main/java/com/ankiride/bridge/ble/ZwiftBleController.kt`

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
```

---

## 5. Phase 1B (`SA-2-ANKI`): 3-Layer AnkiDroid Engine with Window Diagnostics

### 5.1 Instructions for `SA-2-ANKI`
- **Assigned Scope**: `app/src/main/java/com/ankiride/bridge/anki/**` and `app/src/main/res/xml/accessibility_service_config.xml`.
- Log every AnkiDroid action attempt (`FLIP_OR_GOOD`, `FLIP_OR_AGAIN`, `UNDO`, etc.) to `DiagnosticLogger` under `LogCategory.ANKI`, explicitly stating which layer handled the click:
  - **Layer 1**: Exact View ID (`com.ichi2.anki:id/show_answer`, `again_button`, `good_button`, etc.)
  - **Layer 2**: Visible Button Text (`"Show answer"`, `"Again"`, `"Hard"`, `"Good"`, `"Easy"`)
  - **Layer 3**: Window-Relative Geometric Bottom-Bar Tap (`y = 93%`, `x = 12.5% / 37.5% / 62.5% / 87.5%`)
  - **Bounce Guard**: If suppressed by `FLIP_TO_GRADE_GUARD_MS = 350L`, log a warning (`"Ignored grade action within 350ms bounce guard after card flip"`).
- Add `dumpInteractiveWindowsToLog(service: AccessibilityService?)` so the user can tap **"Inspect Windows"** in the UI and see every window package, screen bounds, and visible AnkiDroid button node in the live log.

### 5.2 `app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt`

```kotlin
package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory

class AnkiAccessibilityBridge {

    companion object {
        const val ANKI_PACKAGE = "com.ichi2.anki"
        const val FLIP_TO_GRADE_GUARD_MS = 350L

        val SHOW_ANSWER_IDS = listOf(
            "com.ichi2.anki:id/show_answer",
            "com.ichi2.anki:id/flashcard_layout_flip",
            "com.ichi2.anki:id/answer_options_layout"
        )
        val EASE_1_IDS = listOf("com.ichi2.anki:id/again_button", "com.ichi2.anki:id/flashcard_layout_ease1")
        val EASE_2_IDS = listOf("com.ichi2.anki:id/hard_button", "com.ichi2.anki:id/flashcard_layout_ease2")
        val EASE_3_IDS = listOf("com.ichi2.anki:id/good_button", "com.ichi2.anki:id/flashcard_layout_ease3")
        val EASE_4_IDS = listOf("com.ichi2.anki:id/easy_button", "com.ichi2.anki:id/flashcard_layout_ease4")
        val UNDO_IDS = listOf("com.ichi2.anki:id/action_undo")

        fun dumpInteractiveWindowsToLog(service: AccessibilityService?) {
            if (service == null) {
                DiagnosticLogger.warn(LogCategory.ANKI, "Inspect Windows: BridgeAccessibilityService is NOT connected")
                return
            }
            val windows = service.windows
            if (windows.isNullOrEmpty()) {
                DiagnosticLogger.warn(LogCategory.ANKI, "Inspect Windows: service.windows is empty")
                return
            }
            DiagnosticLogger.info(LogCategory.ANKI, "=== Interactive Windows Dump (${windows.size} windows) ===")
            for ((idx, win) in windows.withIndex()) {
                val bounds = Rect()
                win.getBoundsInScreen(bounds)
                val pkg = win.root?.packageName?.toString() ?: "null"
                DiagnosticLogger.info(LogCategory.ANKI, "Window[$idx]: pkg=$pkg, bounds=$bounds, active=${win.isActive}, focused=${win.isFocused}")
            }
        }
    }

    @Volatile
    var lastFlipTimestampMs: Long = 0L

    fun performAnkiAction(service: AccessibilityService, action: AnkiAction, nowMs: Long = System.currentTimeMillis()): Boolean {
        val ankiWindow: AccessibilityWindowInfo? = service.windows
            ?.firstOrNull { it.root?.packageName?.toString() == ANKI_PACKAGE }

        val ankiRoot: AccessibilityNodeInfo? = ankiWindow?.root
            ?: service.rootInActiveWindow?.takeIf { it.packageName?.toString() == ANKI_PACKAGE }

        if (ankiRoot == null && ankiWindow == null) {
            DiagnosticLogger.warn(LogCategory.ANKI, "Cannot dispatch $action: com.ichi2.anki window not visible on screen")
            return false
        }

        if (ankiRoot != null) {
            val handledByNode = findAndClickButton(ankiRoot, action, nowMs)
            if (handledByNode) return true
        }

        // Layer 3 Fallback: Window-relative geometric tap on AnkiDroid's bottom answer bar
        if (ankiWindow != null && action != AnkiAction.UNDO) {
            if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) {
                DiagnosticLogger.warn(LogCategory.ANKI, "Bounce Guard (350ms) blocked Layer 3 tap for $action")
                return false
            }
            val bounds = Rect()
            ankiWindow.getBoundsInScreen(bounds)
            val tapped = dispatchBottomBarColumnTap(service, bounds, action)
            if (tapped) {
                DiagnosticLogger.info(LogCategory.ANKI, "Layer 3 (Geometric Tap) dispatched for $action in bounds=$bounds")
            } else {
                DiagnosticLogger.error(LogCategory.ANKI, "Layer 3 (Geometric Tap) failed for $action")
            }
            return tapped
        }
        DiagnosticLogger.warn(LogCategory.ANKI, "Action $action could not find matching AnkiDroid button")
        return false
    }

    fun findAndClickButton(
        root: AccessibilityNodeInfo?,
        action: AnkiAction,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (root == null || root.packageName?.toString() != ANKI_PACKAGE) return false
        if (root.isPassword) return false

        return when (action) {
            AnkiAction.FLIP_CARD -> {
                val flipped = clickByIdsOrText(root, SHOW_ANSWER_IDS, listOf("Show answer", "Show Answer"), action)
                if (flipped) lastFlipTimestampMs = nowMs
                flipped
            }
            AnkiAction.FLIP_OR_AGAIN -> clickShowAnswerOrEase(root, EASE_1_IDS, listOf("Again"), action, nowMs)
            AnkiAction.FLIP_OR_HARD -> clickShowAnswerOrEase(root, EASE_2_IDS, listOf("Hard", "Again"), action, nowMs)
            AnkiAction.FLIP_OR_GOOD -> clickShowAnswerOrEase(root, EASE_3_IDS, listOf("Good"), action, nowMs)
            AnkiAction.FLIP_OR_EASY -> clickShowAnswerOrEase(root, EASE_4_IDS, listOf("Easy", "Good"), action, nowMs)
            AnkiAction.UNDO -> clickByIdsOrText(root, UNDO_IDS, listOf("Undo"), action)
        }
    }

    private fun clickShowAnswerOrEase(
        root: AccessibilityNodeInfo,
        easeIds: List<String>,
        easeTexts: List<String>,
        action: AnkiAction,
        nowMs: Long
    ): Boolean {
        if (clickByIdsOrText(root, SHOW_ANSWER_IDS, listOf("Show answer", "Show Answer"), AnkiAction.FLIP_CARD)) {
            lastFlipTimestampMs = nowMs
            DiagnosticLogger.info(LogCategory.ANKI, "Flipped card via Show Answer (armed 350ms bounce guard)")
            return true
        }
        if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) {
            DiagnosticLogger.warn(LogCategory.ANKI, "Bounce Guard (350ms) blocked accidental double-press for $action")
            return false
        }
        return clickByIdsOrText(root, easeIds, easeTexts, action)
    }

    private fun clickByIdsOrText(
        root: AccessibilityNodeInfo,
        viewIds: List<String>,
        fallbackTexts: List<String>,
        action: AnkiAction
    ): Boolean {
        for (id in viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            for (node in nodes) {
                if (!node.isPassword && node.isVisibleToUser && clickNodeOrParent(node)) {
                    DiagnosticLogger.info(LogCategory.ANKI, "Layer 1 (ViewId '$id') clicked for $action")
                    return true
                }
            }
        }
        for (text in fallbackTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text) ?: continue
            for (node in nodes) {
                if (!node.isPassword && node.isVisibleToUser && clickNodeOrParent(node)) {
                    DiagnosticLogger.info(LogCategory.ANKI, "Layer 2 (Text '$text') clicked for $action")
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

---

## 6. Phase 1C (`SA-3-MYWHOOSH`): Standalone `_openbikecontrol._tcp` Server & Dual-Stack IP Fix

### 6.1 Instructions for `SA-3-MYWHOOSH`
- **Assigned Scope**: `app/src/main/java/com/ankiride/bridge/mywhoosh/**` and `app/src/test/java/com/ankiride/bridge/mywhoosh/**`.
- Fix `LanAddressValidator.kt` so Android dual-stack `ServerSocket` connections from `::ffff:127.0.0.1` or `::ffff:192.168.x.x` strip the `::ffff:` IPv4-mapped prefix before checking `IPV4_LITERAL_REGEX` and `isLoopbackAddress || isSiteLocalAddress` (while still rejecting raw `::1` or arbitrary hostnames).
- Log every server bind, mDNS registration callback, client socket connection, binary `0x01` opcode transmission, and `MyShiftGestureFallback` screen tap via `DiagnosticLogger` (`LogCategory.MYWHOOSH`).

### 6.2 `app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt`

```kotlin
package com.ankiride.bridge.mywhoosh

import java.net.InetAddress

object LanAddressValidator {
    private val IPV4_LITERAL_REGEX = Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    fun normalizeIpv4MappedHost(host: String): String {
        val trimmed = host.trim()
        return when {
            trimmed.startsWith("::ffff:", ignoreCase = true) -> trimmed.substring(7)
            trimmed.startsWith("0:0:0:0:0:ffff:", ignoreCase = true) -> trimmed.substring(15)
            else -> trimmed
        }
    }

    fun isLocalOrLanAddress(host: String): Boolean {
        val normalized = normalizeIpv4MappedHost(host)
        if (!IPV4_LITERAL_REGEX.matches(normalized)) return false
        return try {
            val addr = InetAddress.getByName(normalized)
            addr.isLoopbackAddress || addr.isSiteLocalAddress
        } catch (_: Exception) {
            false
        }
    }

    fun isAllowedTargetHost(host: String): Boolean = isLocalOrLanAddress(host)
}
```

### 6.3 `app/src/main/java/com/ankiride/bridge/mywhoosh/OpenBikeControlClient.kt`

```kotlin
package com.ankiride.bridge.mywhoosh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
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
    val connectedClients = CopyOnWriteArrayList<Socket>()

    fun startServer(port: Int = 21587) {
        if (serverSocket != null) return
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val srv = ServerSocket(port)
                serverSocket = srv
                DiagnosticLogger.info(
                    LogCategory.MYWHOOSH,
                    "OpenBikeControl TCP Server listening on port ${srv.localPort}"
                )
                registerMdnsService(srv.localPort)
                while (isActive && !srv.isClosed) {
                    val client = srv.accept()
                    val rawHost = client.inetAddress?.hostAddress ?: ""
                    if (LanAddressValidator.isLocalOrLanAddress(rawHost)) {
                        connectedClients.add(client)
                        DiagnosticLogger.info(
                            LogCategory.MYWHOOSH,
                            "MyWhoosh client connected from $rawHost (total clients=${connectedClients.size})"
                        )
                        // Send initial OpenBikeControl 0x02 Device Status (100% battery, connected flag = 1)
                        sendRawBytes(client, byteArrayOf(MSG_DEVICE_STATUS, 0x64, 0x01))
                    } else {
                        DiagnosticLogger.warn(LogCategory.MYWHOOSH, "Rejected non-LAN socket connection from $rawHost")
                        client.close()
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    DiagnosticLogger.error(LogCategory.MYWHOOSH, "OpenBikeControl server error: ${e.message}")
                }
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
            DiagnosticLogger.info(
                LogCategory.MYWHOOSH,
                "Sent OpenBikeControl binary opcode 0x${"%02X".format(action.openBikeOpcode)} (${action.name}) to ${connectedClients.size} client(s)"
            )
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
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                DiagnosticLogger.info(LogCategory.MYWHOOSH, "mDNS registered: ${info?.serviceName} ($SERVICE_TYPE) on port $port")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                DiagnosticLogger.error(LogCategory.MYWHOOSH, "mDNS registration failed (errorCode=$errorCode)")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                DiagnosticLogger.info(LogCategory.MYWHOOSH, "mDNS service unregistered")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.MYWHOOSH, "Failed to register mDNS: ${e.message}")
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
        DiagnosticLogger.info(LogCategory.MYWHOOSH, "OpenBikeControl TCP server stopped")
    }

    fun close() {
        stopServer()
    }
}
```

---

## 7. Phase 2 (`SA-4-APP-UI`): Custom Button Router, `ON_RESUME` Permissions Fix & Live Diagnostics UI

### 7.1 Instructions for `SA-4-APP-UI`
- **Assigned Scope**: `app/src/main/java/com/ankiride/bridge/service/**`, `app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt`, `app/src/main/AndroidManifest.xml`, and `app/src/test/java/com/ankiride/bridge/service/**`.
- **1. Upgrade `BridgeAccessibilityService.kt`**:
  - Expose `isConnectedFlow: StateFlow<Boolean>` and log service connection/disconnection to `DiagnosticLogger`.
- **2. Upgrade `ActionRouter.kt`**:
  - Check `config.customBindings[ButtonBindingKey(event.button, event.type)]` first! If the user customized that button/gesture in the UI, execute their custom `MappedTargetAction` (`ankiAction` or `myWhooshAction`) and log it. Otherwise, fall back to the deterministic mode mapping (1-Pod Default, 2-Pod Full, Mode 3 Anki-Only, Mode 4 MyWhoosh-Only).
- **3. Upgrade `MainActivity.kt`**:
  - **Fix Permission & Battery Banners Not Going Away**:
    - Observe `Lifecycle.Event.ON_RESUME` via `DisposableEffect(lifecycleOwner)` to re-query:
      1. `isAccessibilityEnabled(context)`: checks `BridgeAccessibilityService.isConnectedFlow.value || Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.contains(packageName)`.
      2. `isBatteryOptimizationIgnored(context)`: checks `powerManager.isIgnoringBatteryOptimizations(packageName)`.
      3. `areBlePermissionsGranted(context)`: checks `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` on Android 12+ (or `ACCESS_FINE_LOCATION` on Android 10–11).
    - When all 3 are `true`, automatically collapse `PermissionsChecklistCard` into a single compact green banner (`"✓ All System Permissions Active (Tap to view)"`) so the warning card disappears!
  - **Remove Mandatory MAC Input Boxes**:
    - Show live connected MAC addresses (`Right Pod: E4:..` / `Left Pod:Off`) with a one-tap `"Pin Connected Pod"` toggle inside an optional collapsed `"Advanced Controller Lock"` section.
  - **Add Custom Button Mapping Editor Card**:
    - Expandable card allowing the user to customize `Single Click` and `Long Press` actions for any Zwift Click v2 button (`+ Rocker`, `Z`, `A`, `B`, `Y`, `– Rocker`, `D-Pad`), plus a `"Reset to Default"` button.
  - **Add Live Diagnostic Log Card with `Copy`, `Clear`, Category Filters & Quick Test Bar**:
    - Displays live `DiagnosticLogger.logs` in a dark monospace scroll box with color-coded severity (`INFO`, `WARN`, `ERROR`).
    - Dedicated **Copy Logs** button (copies `DiagnosticLogger.formatForClipboard()` to `ClipboardManager` + shows Toast), **Clear Logs** button, and **Inspect Windows** button.

### 7.2 `app/src/main/java/com/ankiride/bridge/service/BridgeAccessibilityService.kt`

```kotlin
package com.ankiride.bridge.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BridgeA11yService"
        var activeInstance: BridgeAccessibilityService? = null
            private set

        private val _isConnectedFlow = MutableStateFlow(false)
        val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "BridgeAccessibilityService connected")
        activeInstance = this
        _isConnectedFlow.value = true
        DiagnosticLogger.info(LogCategory.PERMS, "BridgeAccessibilityService connected and active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window events handled directly on demand via service.windows inspection
    }

    override fun onInterrupt() {
        Log.w(TAG, "BridgeAccessibilityService interrupted")
        DiagnosticLogger.warn(LogCategory.PERMS, "BridgeAccessibilityService interrupted")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Security Gate SG-2 check: reject all external onStartCommand triggers
        Log.w(TAG, "Rejected external onStartCommand trigger")
        return START_NOT_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (activeInstance == this) {
            activeInstance = null
            _isConnectedFlow.value = false
            DiagnosticLogger.warn(LogCategory.PERMS, "BridgeAccessibilityService unbound")
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (activeInstance == this) {
            activeInstance = null
            _isConnectedFlow.value = false
        }
        super.onDestroy()
    }
}
```

### 7.3 `app/src/main/java/com/ankiride/bridge/service/ActionRouter.kt`

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

        // 1. Check user custom button overrides first
        val customTarget = config.customBindings[ButtonBindingKey(event.button, event.type)]
        if (customTarget != null) {
            DiagnosticLogger.info(
                LogCategory.ROUTER,
                "Custom binding: ${event.button.name} (${event.type.name}) -> ${customTarget.label}"
            )
            customTarget.ankiAction?.let { ankiDispatcher.dispatch(it) }
            customTarget.myWhooshAction?.let { mw ->
                scope.launch { myWhooshDispatcher.dispatch(mw, config) }
            }
            return
        }

        // 2. Otherwise use deterministic mode defaults
        when (config.mode) {
            OperatingMode.MODE_3_ANKI_ONLY -> {
                val ankiAction = mapAnkiOnlyMode(event) ?: return
                DiagnosticLogger.info(LogCategory.ROUTER, "Mode 3 (Anki-Only): ${event.button.name} -> $ankiAction")
                ankiDispatcher.dispatch(ankiAction)
            }
            OperatingMode.MODE_4_MYWHOOSH_ONLY -> {
                val mwAction = mapMyWhooshOnlyMode(event, config) ?: return
                DiagnosticLogger.info(LogCategory.ROUTER, "Mode 4 (MyWhoosh-Only): ${event.button.name} -> $mwAction")
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
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: ${event.button.name} (${event.type.name}) -> $mwAction")
                scope.launch { myWhooshDispatcher.dispatch(mwAction, config) }
            }
            ClickButton.Z -> {
                if (event.type == PressType.LONG_PRESS) {
                    DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: Z (LONG_PRESS) -> UNDO")
                    ankiDispatcher.dispatch(AnkiAction.UNDO)
                } else if (config.useZForShiftDownInSinglePodMode) {
                    DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: Z (SINGLE_CLICK) -> SHIFT_DOWN")
                    scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
                } else {
                    DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: Z (SINGLE_CLICK) -> FLIP_OR_EASY")
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASY)
                }
            }
            ClickButton.A -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.FLIP_OR_EASY else AnkiAction.FLIP_OR_GOOD
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: A (${event.type.name}) -> $action")
                ankiDispatcher.dispatch(action)
            }
            ClickButton.B -> {
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: B -> FLIP_OR_HARD")
                ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            }
            ClickButton.Y -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_AGAIN
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: Y (${event.type.name}) -> $action")
                ankiDispatcher.dispatch(action)
            }
            else -> {}
        }
    }

    private fun routeDualPodCombinedMode(event: ButtonGestureEvent, config: AppConfiguration) {
        when (event.button) {
            ClickButton.A -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_GOOD)
            ClickButton.B -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            ClickButton.Y -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_AGAIN)
            ClickButton.Z -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_EASY
                ankiDispatcher.dispatch(action)
            }
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

### 7.4 `app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt`

```kotlin
package com.ankiride.bridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ankiride.bridge.anki.AnkiAccessibilityBridge
import com.ankiride.bridge.ble.ZwiftBleController
import com.ankiride.bridge.core.*
import com.ankiride.bridge.service.BridgeAccessibilityService
import com.ankiride.bridge.service.BridgeForegroundService

class MainActivity : ComponentActivity() {

    private val permissionRefreshTrigger = MutableStateFlow(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionRefreshTrigger.value += 1
        DiagnosticLogger.info(LogCategory.PERMS, "BLE Permission result: $grants")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBlePermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val refreshCount by permissionRefreshTrigger.collectAsState()
                    AnkiRideDashboard(
                        permissionRefreshCount = refreshCount,
                        onRequestPermissions = { requestBlePermissions() },
                        onOpenAccessibility = { openAccessibilitySettings() },
                        onOpenBatteryOpt = { requestIgnoreBatteryOptimizations() }
                    )
                }
            }
        }
    }

    private fun requestBlePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            permissionRefreshTrigger.value += 1
        }
    }

    private fun openAccessibilitySettings() {
        DiagnosticLogger.info(LogCategory.PERMS, "Opening Android Accessibility Settings")
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestIgnoreBatteryOptimizations() {
        DiagnosticLogger.info(LogCategory.PERMS, "Requesting battery optimization exemption")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    if (BridgeAccessibilityService.activeInstance != null || BridgeAccessibilityService.isConnectedFlow.value) {
        return true
    }
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(context.packageName, ignoreCase = true)
}

fun isBatteryOptimizationExempt(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun areBlePermissionsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun AnkiRideDashboard(
    permissionRefreshCount: Int,
    onRequestPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOpt: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isRunning by BridgeForegroundService.isRunningFlow.collectAsState()
    val bleState by BridgeForegroundService.connectionStateFlow.collectAsState()
    val a11yServiceConnected by BridgeAccessibilityService.isConnectedFlow.collectAsState()
    val lastRightMac by ZwiftBleController.lastRightMac.collectAsState()
    val lastLeftMac by ZwiftBleController.lastLeftMac.collectAsState()

    var selectedMode by remember { mutableStateOf(OperatingMode.MODE_1_TABLET_SPLIT_SCREEN) }
    var rightPodOnly by remember { mutableStateOf(true) }
    var useZForShiftDown by remember { mutableStateOf(true) }
    var useTouchFallback by remember { mutableStateOf(true) }
    var lockToConnectedPods by remember { mutableStateOf(false) }
    var customBindings by remember { mutableStateOf<Map<ButtonBindingKey, MappedTargetAction>>(emptyMap()) }

    // Reactive permission states updated on ON_RESUME and when service state changes
    var a11yEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var batteryExempt by remember { mutableStateOf(isBatteryOptimizationExempt(context)) }
    var bleGranted by remember { mutableStateOf(areBlePermissionsGranted(context)) }

    LaunchedEffect(a11yServiceConnected, permissionRefreshCount) {
        a11yEnabled = isAccessibilityServiceEnabled(context)
        batteryExempt = isBatteryOptimizationExempt(context)
        bleGranted = areBlePermissionsGranted(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = isAccessibilityServiceEnabled(context)
                batteryExempt = isBatteryOptimizationExempt(context)
                bleGranted = areBlePermissionsGranted(context)
                DiagnosticLogger.info(
                    LogCategory.PERMS,
                    "ON_RESUME permission check: A11y=$a11yEnabled, BatteryExempt=$batteryExempt, BLE=$bleGranted"
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun buildCurrentConfig(): AppConfiguration {
        return AppConfiguration(
            mode = selectedMode,
            rightPodOnlyNoZwift = rightPodOnly,
            useZForShiftDownInSinglePodMode = useZForShiftDown,
            useMyShiftTouchFallbackWhenNoClient = useTouchFallback,
            pinnedRightPodMac = if (lockToConnectedPods) lastRightMac else null,
            pinnedLeftPodMac = if (lockToConnectedPods) lastLeftMac else null,
            customBindings = customBindings
        )
    }

    // Keep live service config synced when toggles or custom bindings change
    LaunchedEffect(selectedMode, rightPodOnly, useZForShiftDown, useTouchFallback, lockToConnectedPods, customBindings) {
        if (isRunning) {
            BridgeForegroundService.startService(context, buildCurrentConfig())
        }
    }

    val scrollState = rememberScrollState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AnkiRide Bridge",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "100% Standalone OpenBikeControl Server + AnkiDroid Reviewer (No BikeControl or Zwift App Needed)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Permissions Checklist (Auto-collapses when all 3 are granted!)
            PermissionsChecklistCard(
                a11yEnabled = a11yEnabled,
                batteryExempt = batteryExempt,
                bleGranted = bleGranted,
                onRequestPermissions = onRequestPermissions,
                onOpenAccessibility = onOpenAccessibility,
                onOpenBatteryOpt = onOpenBatteryOpt
            )

            // 2. Connection Status Card
            ConnectionStatusCard(
                bleState = bleState,
                isRunning = isRunning,
                lastRightMac = lastRightMac,
                lastLeftMac = lastLeftMac,
                onToggleService = {
                    if (isRunning) {
                        BridgeForegroundService.stopService(context)
                    } else {
                        BridgeForegroundService.startService(context, buildCurrentConfig())
                    }
                }
            )

            // 3. Operating Mode Selector
            Text(
                text = "Operating Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            ModeSelector(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it }
            )

            // 4. Hardware & Shifting Behavior (No manual MAC typing needed)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Hardware & Shifting Options",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "1-Controller Default (Right Pod Only)", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Zero 24h sleep lock; switch off to use both Left & Right pods",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rightPodOnly,
                            onCheckedChange = { rightPodOnly = it }
                        )
                    }

                    if (rightPodOnly) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Top Z Button Shifts Down", fontWeight = FontWeight.Medium)
                                Text(
                                    text = "Instant shifting: '+' = Shift Up, 'Z' = Shift Down, Y/B/A = Anki",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useZForShiftDown,
                                onCheckedChange = { useZForShiftDown = it }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "MyShift Touch Tap Fallback", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Taps MyWhoosh screen arrows if OpenBikeControl icon isn't paired",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useTouchFallback,
                            onCheckedChange = { useTouchFallback = it }
                        )
                    }

                    if (lastRightMac != null || lastLeftMac != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Lock to Connected Controller", fontWeight = FontWeight.Medium)
                                Text(
                                    text = "Pin MAC (${lastRightMac ?: lastLeftMac}) so nearby controllers are ignored",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = lockToConnectedPods,
                                onCheckedChange = { lockToConnectedPods = it }
                            )
                        }
                    }
                }
            }

            // 5. Custom Button Mapping Editor
            CustomButtonMappingCard(
                rightPodOnly = rightPodOnly,
                customBindings = customBindings,
                onUpdateBinding = { key, target ->
                    customBindings = if (target == null) {
                        customBindings - key
                    } else {
                        customBindings + (key to target)
                    }
                },
                onResetAll = {
                    customBindings = emptyMap()
                    DiagnosticLogger.info(LogCategory.ROUTER, "Reset all custom button bindings to default preset")
                }
            )

            // 6. Detailed Live Diagnostic Log Card with Copy, Clear & Window Inspector
            DiagnosticLogCard()
        }
    }
}

@Composable
fun PermissionsChecklistCard(
    a11yEnabled: Boolean,
    batteryExempt: Boolean,
    bleGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOpt: () -> Unit
) {
    val allReady = a11yEnabled && batteryExempt && bleGranted
    var expandedWhenReady by remember { mutableStateOf(false) }

    if (allReady && !expandedWhenReady) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedWhenReady = true },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFFA5D6A7)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All System Permissions Active (Accessibility, Battery & BLE)",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "Show",
                    color = Color(0xFFA5D6A7),
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Setup & Permissions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                if (allReady) {
                    Text(
                        text = "Hide",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { expandedWhenReady = false }
                    )
                }
            }

            // Accessibility Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (a11yEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (a11yEnabled) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (a11yEnabled) "Accessibility Service (Active)" else "Accessibility Service (Required)",
                        fontSize = 14.sp
                    )
                }
                OutlinedButton(onClick = onOpenAccessibility) {
                    Text(if (a11yEnabled) "Settings" else "Enable")
                }
            }

            // Battery Optimization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (batteryExempt) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (batteryExempt) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (batteryExempt) "Battery Unrestricted (Active)" else "Battery Optimization (Restricting)",
                        fontSize = 14.sp
                    )
                }
                OutlinedButton(onClick = onOpenBatteryOpt) {
                    Text(if (batteryExempt) "Exempt ✓" else "Allow Background")
                }
            }

            // Bluetooth LE Permissions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (bleGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (bleGranted) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (bleGranted) "Bluetooth LE Permissions (Granted)" else "Bluetooth LE Permissions (Missing)",
                        fontSize = 14.sp
                    )
                }
                OutlinedButton(onClick = onRequestPermissions) {
                    Text(if (bleGranted) "Granted ✓" else "Grant")
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    bleState: BleConnectionState,
    isRunning: Boolean,
    lastRightMac: String?,
    lastLeftMac: String?,
    onToggleService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRunning) "OPENBIKECONTROL SERVER ACTIVE" else "BRIDGE STOPPED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isRunning) "Port 21587 • mDNS _openbikecontrol._tcp" else "Tap Start to connect Zwift Click & advertise to MyWhoosh",
                        fontSize = 12.sp,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onToggleService,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isRunning) "Stop Bridge" else "Start Bridge")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (bleState) {
                    BleConnectionState.DISCONNECTED -> Text("Controller: Disconnected", fontSize = 13.sp)
                    BleConnectionState.SCANNING -> Text("Controller: Scanning for Zwift Click v2...", fontSize = 13.sp)
                    BleConnectionState.CONNECTING, BleConnectionState.HANDSHAKING -> Text("Controller: Sending 'RideOn' Handshake...", fontSize = 13.sp)
                    BleConnectionState.CONNECTED_RIGHT_ONLY -> {
                        PodBadge("Right Pod ${lastRightMac?.takeLast(5) ?: ""}", connected = true)
                        PodBadge("Left Pod", connected = false)
                    }
                    BleConnectionState.CONNECTED_BOTH_PODS -> {
                        PodBadge("Right Pod ${lastRightMac?.takeLast(5) ?: ""}", connected = true)
                        PodBadge("Left Pod ${lastLeftMac?.takeLast(5) ?: ""}", connected = true)
                    }
                    BleConnectionState.ERROR -> Text(
                        "Controller: Connection Error (See Live Log below)",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PodBadge(label: String, connected: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (connected) Color(0xFF2E7D32) else Color(0xFF616161),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label: ${if (connected) "Connected" else "Off"}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CustomButtonMappingCard(
    rightPodOnly: Boolean,
    customBindings: Map<ButtonBindingKey, MappedTargetAction>,
    onUpdateBinding: (ButtonBindingKey, MappedTargetAction?) -> Unit,
    onResetAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val buttonsToConfigure = remember(rightPodOnly) {
        val coreRight = listOf(ClickButton.SHIFT_UP_RIGHT, ClickButton.Z, ClickButton.A, ClickButton.B, ClickButton.Y)
        val coreLeft = listOf(ClickButton.SHIFT_DOWN_LEFT, ClickButton.LEFT, ClickButton.RIGHT, ClickButton.UP, ClickButton.DOWN)
        if (rightPodOnly) coreRight else coreRight + coreLeft
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Custom Button Mapping (${if (customBindings.isEmpty()) "Using Default Preset" else "${customBindings.size} Custom Overrides"})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (expanded) "Tap any button below to customize Single Click or Long Press" else "Tap to customize what each controller button triggers",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (expanded) "Collapse ▲" else "Customize ▼",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onResetAll) {
                        Text("Reset All to Default Preset", fontSize = 12.sp)
                    }
                }

                buttonsToConfigure.forEach { button ->
                    ButtonBindingRow(
                        button = button,
                        pressType = PressType.SINGLE_CLICK,
                        currentOverride = customBindings[ButtonBindingKey(button, PressType.SINGLE_CLICK)],
                        onSelect = { onUpdateBinding(ButtonBindingKey(button, PressType.SINGLE_CLICK), it) }
                    )
                    ButtonBindingRow(
                        button = button,
                        pressType = PressType.LONG_PRESS,
                        currentOverride = customBindings[ButtonBindingKey(button, PressType.LONG_PRESS)],
                        onSelect = { onUpdateBinding(ButtonBindingKey(button, PressType.LONG_PRESS), it) }
                    )
                }
            }
        }
    }
}

@Composable
fun ButtonBindingRow(
    button: ClickButton,
    pressType: PressType,
    currentOverride: MappedTargetAction?,
    onSelect: (MappedTargetAction?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val gestureLabel = if (pressType == PressType.SINGLE_CLICK) "Single Click" else "Long Press (450ms)"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${button.displayName} • $gestureLabel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = currentOverride?.label ?: "Default Mode Mapping",
                fontSize = 11.sp,
                color = if (currentOverride != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            OutlinedButton(onClick = { menuExpanded = true }) {
                Text(if (currentOverride != null) "Change" else "Default", fontSize = 12.sp)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Use Default Mode Mapping") },
                    onClick = {
                        onSelect(null)
                        menuExpanded = false
                    }
                )
                MappedTargetAction.entries.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.label) },
                        onClick = {
                            onSelect(target)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticLogCard() {
    val context = LocalContext.current
    val logs by DiagnosticLogger.logs.collectAsState()
    var selectedCategory by remember { mutableStateOf<LogCategory?>(null) }

    val filteredLogs = remember(logs, selectedCategory) {
        if (selectedCategory == null) logs else logs.filter { it.category == selectedCategory }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Live Diagnostic Log (${filteredLogs.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Real-time BLE packets, Anki clicks & OpenBikeControl events",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Buttons: Copy, Clear, Inspect Windows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val text = DiagnosticLogger.formatForClipboard(selectedCategory)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("AnkiRide Diagnostic Log", text))
                        Toast.makeText(context, "Copied ${filteredLogs.size} log lines to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy Logs", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        DiagnosticLogger.clear()
                        Toast.makeText(context, "Cleared diagnostic log", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Logs", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        AnkiAccessibilityBridge.dumpInteractiveWindowsToLog(BridgeAccessibilityService.activeInstance)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Inspect Windows", fontSize = 12.sp)
                }
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("ALL", fontSize = 11.sp) }
                )
                LogCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text(category.name, fontSize = 11.sp) }
                    )
                }
            }

            // Monospace Log Viewer Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color(0xFF0D1117), shape = RoundedCornerShape(8.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (filteredLogs.isEmpty()) {
                    Text(
                        text = "No log entries yet. Tap 'Start Bridge' or 'Inspect Windows' to see live events.",
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filteredLogs.takeLast(120).reversed().forEach { entry ->
                            val color = when (entry.level) {
                                LogLevel.ERROR -> Color(0xFFFF7B72)
                                LogLevel.WARN -> Color(0xFFD29922)
                                LogLevel.INFO -> Color(0xFFC9D1D9)
                            }
                            Text(
                                text = "[${entry.formattedTime}] [${entry.category.name}] ${entry.message}",
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeSelector(
    selectedMode: OperatingMode,
    onModeSelected: (OperatingMode) -> Unit
) {
    val modes = listOf(
        Triple(
            OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            "Tablet Split-Screen",
            "Runs MyWhoosh + native AnkiDroid side-by-side. Connects over 127.0.0.1; A/B/Y/Z clicks AnkiDroid Reviewer."
        ),
        Triple(
            OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
            "Tablet Anki + Phone MyWhoosh",
            "AnkiDroid full-screen on Tablet; MyWhoosh on phone. MyWhoosh connects over local Wi-Fi LAN via mDNS."
        ),
        Triple(
            OperatingMode.MODE_3_ANKI_ONLY,
            "Anki-Only (Study)",
            "Pure study mode. All buttons mapped to AnkiDroid card review and undo."
        ),
        Triple(
            OperatingMode.MODE_4_MYWHOOSH_ONLY,
            "MyWhoosh-Only (Ride)",
            "Pure ride mode. All buttons mapped to MyWhoosh shifting, steering, UI toggle, and emotes."
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { (mode, title, description) ->
            val isSelected = mode == selectedMode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(mode) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

---

## 8. Mandatory Review & Security Gates (`RG-0` through `RG-5`)

Every subagent must pass its assigned gate before the Orchestrator advances to the next phase.

| Gate ID | Target Phase / Subagent | Review & Security Verification Criteria | Automated Check Command |
| :--- | :--- | :--- | :--- |
| **`RG-0`** | Phase 0 (`ORCHESTRATOR`) | `Contracts.kt` defines `MappedTargetAction`, `ButtonBindingKey`, and `customBindings`; `DiagnosticLogger.kt` implements `MAX_LOG_ENTRIES = 500`, `formatForClipboard`, and `clear()`. | `grep -q "object DiagnosticLogger" app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt` |
| **`RG-1`** | Phase 1A (`SA-1-BLE`) | `ZwiftPacketParser` enforces `MAX_PACKET_SIZE = 64` and `MAX_VARINT_BYTES = 5`; `ZwiftBleController` logs scan/GATT/handshake/packet events to `DiagnosticLogger` and exposes `lastRightMac` / `lastLeftMac`. | `grep -q "DiagnosticLogger" app/src/main/java/com/ankiride/bridge/ble/ZwiftBleController.kt` |
| **`RG-2`** | Phase 1B (`SA-2-ANKI`) | `accessibility_service_config.xml` pins `com.ichi2.anki,com.mywhoosh.whooshgame`; `AnkiAccessibilityBridge` checks `!node.isPassword`, enforces `FLIP_TO_GRADE_GUARD_MS = 350L`, and provides `dumpInteractiveWindowsToLog`. | `grep -q "dumpInteractiveWindowsToLog" app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt` |
| **`RG-3`** | Phase 1C (`SA-3-MYWHOOSH`) | `LanAddressValidator` normalizes `::ffff:` IPv4-mapped IPv6 addresses and blocks public IPs/hostnames; `OpenBikeControlClient` logs TCP server bind, mDNS registration, and `0x01` opcodes. | `grep -q "normalizeIpv4MappedHost" app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt` |
| **`RG-4`** | Phase 2 (`SA-4-APP-UI`) | `MainActivity.kt` refreshes permissions on `Lifecycle.Event.ON_RESUME`, auto-collapses the checklist card when all 3 are granted, provides **Copy Logs** / **Clear Logs** / **Inspect Windows** buttons, and supports Custom Button Mapping without raw MAC text fields. | `grep -q "ON_RESUME" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt && grep -q "Copy Logs" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt` |
| **`RG-5`** | Phase 3 (`SA-5-REVIEWER`) | All unit tests (`ZwiftPacketParserTest`, `ZwiftGestureEngineTest`, `LanAddressValidatorTest`, `OpenBikeControlClientTest`, `ActionRouterTest`, `AnkiAccessibilityBridgeTest`) compile and pass with 0 errors. | `./verify.sh && ./gradlew testDebugUnitTest --no-daemon` |

### 8.1 Updated `verify.sh` Script for `RG-0` .. `RG-5`

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "=== [RG-0] Checking Core Contracts & DiagnosticLogger ==="
test -f app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt
grep -q "MappedTargetAction" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "formatForClipboard" app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt

echo "=== [RG-1] Checking BLE Bounds, Handshake & Diagnostic Logging ==="
grep -q "MAX_VARINT_BYTES = 5" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt
grep -q "MAX_PACKET_SIZE = 64" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt
grep -q "DiagnosticLogger" app/src/main/java/com/ankiride/bridge/ble/ZwiftBleController.kt

echo "=== [RG-2] Checking Accessibility Package Pinning, Password Guard & Window Inspector ==="
grep -q 'android:packageNames="com.ichi2.anki,com.mywhoosh.whooshgame"' app/src/main/res/xml/accessibility_service_config.xml
grep -q "isPassword" app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt
grep -q "dumpInteractiveWindowsToLog" app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt

echo "=== [RG-3] Checking Dual-Stack IPv4-Mapped Normalization & Network Security Config ==="
grep -q 'cleartextTrafficPermitted="false"' app/src/main/res/xml/network_security_config.xml
grep -q "normalizeIpv4MappedHost" app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt
grep -q "isSiteLocalAddress" app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt

echo "=== [RG-4] Checking ON_RESUME Permission Auto-Refresh, Copy/Clear Log UI & Custom Mapping ==="
grep -q "ON_RESUME" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "Copy Logs" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "Clear Logs" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "CustomButtonMappingCard" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt

echo "=== [RG-5] Running Full Unit Test Suite ==="
./gradlew testDebugUnitTest --no-daemon

echo "ALL REVIEW & SECURITY GATES (RG-0 .. RG-5) PASSED."
```

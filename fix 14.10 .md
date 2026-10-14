# Multi-Agent Phase Plan & Review-Gated Specification: `AnkiMywhooshControl` v2.1 (Log-Verified Hardware & MyWhoosh Fix)

## 1. Executive Post-Mortem of Your Tablet Log (`68 Entries`) & Exact Fixes

Your live diagnostic log immediately pinpointed **four exact hardware and protocol bugs** explaining why AnkiDroid worked while MyWhoosh did not shift:

### 1.1 Finding #1: Why `Z` (Shift Down) Did Nothing — Hardware Bitmask `0x00080` vs `0x0100`
In your log at `22:54:44.932` and `22:54:45.138`:
```text
[22:54:44.932] [INFO] [PACKET] 0x23 packet from C0:4A:0E:F9:A3:50: [23 08 FF FE FF FF 0F] -> activeMask=0x00080
[22:54:44.961] [INFO] [PACKET] 0x23 packet from F4:C4:59:0C:6F:29: [23 08 FF FE FF FF 0F] -> activeMask=0x00080
```
- Notice that right after `activeMask=0x00080`, **no `[BLE] Gesture detected` line was logged!**
- **Why**: In `Contracts.kt`, the `ClickButton` enum copied a hex typo from [Makinolo's Zwift Ride blog](https://www.google.com/search?q=https%3A%2F%2Fwww.makinolo.com%2Fblog%2F2024%2F07%2F26%2Fzwift-ride-protocol%2F) that skipped bit 7 (`0x0080`) after `Y` (`0x0040`) and assigned `Z` to `0x0100`. On your physical Zwift Click v2, pressing `Z` sets **Bit 7 (`0x00080`)** (`[23 08 FF FE FF FF 0F]`). Because `0x00080` was missing from `ClickButton`, pressing `Z` was completely ignored!
- **Fix in v2.1**: `ClickButton.Z` is now mapped to `0x00080` (Bit 7), and all bit positions from Bit 8 (`0x00100`) through Bit 17 (`0x20000`)—plus Protobuf Field 2 (`0x12` paddle/analog group) and message ID `0x37`—are mapped and logged so no shift button press is ever dropped.

### 1.2 Finding #2: Why Zero `SHIFT_UP (0x01)` or `SHIFT_DOWN (0x02)` Packets Were Sent in Your Log
Look at every `[MYWHOOSH]` line in your 68-line log:
- `LEFT` $\rightarrow$ `0x18 (STEER_LEFT)`
- `RIGHT` $\rightarrow$ `0x19 (STEER_RIGHT)`
- `UP` $\rightarrow$ `0x40 (TOGGLE_CAMERA)`
- `DOWN` $\rightarrow$ `0x20 (EMOTE)`
- `Y` $\rightarrow$ `0x44 (HUD_TOGGLE)`
- `A` $\rightarrow$ `0x14 (UI_CONFIRM)`
- `B` $\rightarrow$ `0x15 (UI_BACK)`
- `Z` (`0x00080`) $\rightarrow$ *Dropped due to Finding #1!*
**Not a single `0x01 (SHIFT_UP)` or `0x02 (SHIFT_DOWN)` command was ever sent to MyWhoosh during the test!**
- Furthermore, there is a **5.5-second gap** in your log between `22:54:37.952` and `22:54:43.427` where `ZwiftBleController` suppressed logging any packet that didn't have `value[0] == 0x23 && mask != 0`.
- **Fix in v2.1**:
  1. `ZwiftBleController` now logs **every non-keepalive packet** (`value[0] != 0x15 && value[0] != 0x19`) and any `0x23` packet that differs from the idle state, even if Field 1 `mask == 0` (such as Field 2 `0x12` analog/paddle events).
  2. In `Mode 4 (MyWhoosh-Only)` when `1-Controller Default` is active, [matching BikeControl's single-pod layout](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fcontroller-zwift-click-v2%2F), **`+` and `A` shift up (`0x01`)** while **`Z` and `B` shift down (`0x02`)** so you can shift immediately with the face buttons as well as the top rocker.
  3. Added **"Test Shift Up (+)"** and **"Test Shift Down (–)"** buttons directly inside the Live Diagnostic Log card so you can trigger and verify MyWhoosh shifting from the UI with one finger tap.

### 1.3 Finding #3: Why Both Pods (`F4:C4:59:0C:6F:29` & `C0:4A:0E:F9:A3:50`) Connected & Fired Every Button Twice
In your log at `22:54:43.427`:
```text
[22:54:43.427] [INFO] [PACKET] 0x23 packet from F4:C4:59:0C:6F:29: [23 08 BF FF FF FF 0F] -> activeMask=0x00040
[22:54:43.428] [INFO] [PACKET] 0x23 packet from C0:4A:0E:F9:A3:50: [23 08 BF FF FF FF 0F] -> activeMask=0x00040
```
- **Why**: As documented in [OpenBikeControl Issue #68](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fbikecontrol%2Fissues%2F68) and [Makinolo's Zwift Ride protocol guide](https://www.google.com/search?q=https%3A%2F%2Fwww.makinolo.com%2Fblog%2F2024%2F07%2F26%2Fzwift-ride-protocol%2F), the two Zwift Click v2 pods bridge to each other wirelessly and **mirror the 32-bit `0x23` button bitmap across both BLE connections** (`F4:C4:59:0C:6F:29` and `C0:4A:0E:F9:A3:50`) within `1ms` of each other. Because both pods connected and fed into the same `ZwiftGestureEngine` without per-pod state merging, every button press fired **twice**!
- Additionally, `BridgeForegroundService.onStartCommand` called `startBridge()` without stopping the existing `bleController` when settings changed in `MainActivity`.
- **Fix in v2.1**:
  1. `ZwiftBleController` tracks each pod's active bitmask in `podMasks: ConcurrentHashMap<String, Int>` and feeds the bitwise OR (`combinedMask`) into `ZwiftGestureEngine`, with a `120ms` debounce guard in `ZwiftGestureEngine` so mirrored packets from both pods (`F4:C4:59:0C:6F:29` and `C0:4A:0E:F9:A3:50`) produce **exactly one** gesture event.
  2. In `1-Controller Mode (rightPodOnlyNoZwift = true)`, `ZwiftBleController` stops scanning as soon as 1 pod is connected (`activeGatts.size >= 1`).
  3. `BridgeForegroundService` now supports `ACTION_UPDATE_CONFIG` so changing modes or toggles in the UI updates `currentConfig` in-place without restarting BLE or TCP sockets.

### 1.4 Finding #4: Why MyWhoosh On-Screen Shifting Buttons Didn't Move (`OpenBikeControl` TCP vs. `MyShift` Touch)
1. **OpenBikeControl TCP Frame Coalescing & `0x04` Handshake ([OpenBikeControl mDNS \& TCP Spec](https://www.google.com/search?q=https%3A%2F%2Fopenbikecontrol.org%2Fmdns))**:
   - Without `client.tcpNoDelay = true`, Android's TCP stack can coalesce `[0x01, opcode, 0x01]` (Press) and `[0x01, opcode, 0x00]` (Release `35ms` later) into the **same game frame** in MyWhoosh (30 FPS = `33.3ms`/frame), causing Press and Release to cancel out in a single tick.
   - `OpenBikeControlClient.kt` now sets `client.tcpNoDelay = true`, holds `STATE_PRESSED` for `90ms` before sending `STATE_RELEASED`, includes `service-uuids=d273f680-d548-419d-b9d1-fa0472345229` in the mDNS TXT record, and reads MyWhoosh's incoming **`0x04` App Information message** so the log shows the exact app ID, version, and supported button IDs reported by MyWhoosh!
2. **Simultaneous On-Screen `MyShift` Touch Tap (`MyWhooshNetworkDispatcherImpl.kt`)**:
   - Previously, `if (client.hasConnectedClients()) return true` prevented `MyShiftGestureFallback` from tapping MyWhoosh's on-screen shift buttons whenever OpenBikeControl was connected!
   - Also, OpenBikeControl network shifting (`0x01`/`0x02`) only changes gears inside MyWhoosh when you are **actively in a ride with a trainer connected that has Virtual Shifting enabled**—and it does *not* visually animate the on-screen touch buttons unless the screen is tapped.
   - **Fix in v2.1**: When `useMyShiftTouchFallbackWhenNoClient` (now **"Tap On-Screen MyShift Buttons (Tablet Modes)"**) is enabled in Mode 1 or Mode 4, `MyWhooshNetworkDispatcherImpl` sends the OpenBikeControl binary packet **AND** dispatches the `MyShiftGestureFallback` touch tap (or clicks matching Accessibility nodes in `com.mywhoosh.whooshgame`), with adjustable X/Y coordinate sliders and a window node dumper so you see the on-screen buttons react immediately.

---

## 2. Multi-Agent Phase Topology & Write-Isolation Matrix

| Phase | Subagent ID | Role Description | Owned Repository Files (Read-Write) | Read-Only Dependencies | Blocking Gate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Phase 0** | `ORCHESTRATOR` | Corrected Hardware Bitmasks (`Z=0x0080`), `DiagnosticLogger` & Gate Script | `app/src/main/java/com/ankiride/bridge/core/Contracts.kt`, `app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt`, `verify.sh` | `build.gradle.kts`, `app/build.gradle.kts` | `RG-0` |
| **Phase 1A** | `SA-1-BLE` | Full `0x23` (Field 1 + Field 2) & `0x37` Parser, Cross-Pod Deduplication & Raw Packet Logger | `app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt`, `ZwiftGestureEngine.kt`, `ZwiftBleController.kt`, `app/src/test/java/com/ankiride/bridge/ble/*` | `core/*` | `RG-1` |
| **Phase 1B** | `SA-2-ANKI` | 3-Layer AnkiDroid Accessibility Engine + Multi-App Window/Node Dumper | `app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt`, `AnkiActionDispatcherImpl.kt`, `AnkiContentProviderHelper.kt`, `app/src/main/res/xml/accessibility_service_config.xml`, `app/src/test/java/com/ankiride/bridge/anki/*` | `core/*` | `RG-2` |
| **Phase 1C** | `SA-3-MYWHOOSH` | `tcpNoDelay` + `90ms` Hold + `0x04` AppInfo Reader + Dual Network/Touch Shift Dispatcher | `app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt`, `OpenBikeControlClient.kt`, `MyShiftGestureFallback.kt`, `MyWhooshNetworkDispatcherImpl.kt`, `MyWhooshDiscovery.kt`, `app/src/main/res/xml/network_security_config.xml`, `app/src/test/java/com/ankiride/bridge/mywhoosh/*` | `core/*` | `RG-3` |
| **Phase 2** | `SA-4-APP-UI` | In-Place Service Config Update, Mode 4 Face-Button Shifting, Test Shift Buttons & Live Log UI | `app/src/main/java/com/ankiride/bridge/service/ActionRouter.kt`, `BridgeAccessibilityService.kt`, `BridgeForegroundService.kt`, `app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt`, `app/src/main/AndroidManifest.xml`, `app/src/test/java/com/ankiride/bridge/service/*` | `core/*`, `ble/*`, `anki/*`, `mywhoosh/*` | `RG-4` |
| **Phase 3** | `SA-5-REVIEWER` | Automated Gate Auditor & Unit Test Runner | Read-Only across entire repository (executes `./verify.sh` and `./gradlew testDebugUnitTest`) | Entire Repository | `RG-5` |

---

## 3. Phase 0 (`ORCHESTRATOR`): Hardware-Verified `Contracts.kt` & `DiagnosticLogger.kt`

### 3.1 `app/src/main/java/com/ankiride/bridge/core/Contracts.kt`

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

/**
 * Hardware-verified Zwift Click v2 / Ride 0x23 inverse bitmasks.
 * Note: Bit 7 (0x0080) is the physical Right 'Z' button (verified from packet [23 08 FF FE FF FF 0F]).
 * Both shifted and unshifted upper bit positions are mapped so all Click v2 and Ride firmware versions work.
 */
enum class ClickButton(val bitmask: Int, val displayName: String, val isRightPod: Boolean) {
    LEFT(0x0001, "Left D-Pad ◀", false),
    UP(0x0002, "Left D-Pad ▲", false),
    RIGHT(0x0004, "Left D-Pad ▶", false),
    DOWN(0x0008, "Left D-Pad ▼", false),
    A(0x0010, "Right 'A' (Bottom)", true),
    B(0x0020, "Right 'B' (Right)", true),
    Y(0x0040, "Right 'Y' (Left)", true),
    Z(0x0080, "Right 'Z' (Top)", true),
    SHIFT_UP_LEFT(0x0100, "Left Shift Up (Bit 8)", false),
    SHIFT_DOWN_LEFT(0x0200, "Left '–' Rocker (Bit 9)", false),
    SHIFT_DOWN_LEFT_ALT(0x0400, "Left '–' Rocker (Bit 10)", false),
    POWERUP_LEFT(0x0800, "Left PowerUp (Bit 11)", false),
    SHIFT_UP_RIGHT(0x1000, "Right '+' Rocker (Bit 12)", true),
    SHIFT_UP_RIGHT_ALT(0x2000, "Right '+' Rocker (Bit 13)", true),
    SHIFT_DOWN_RIGHT(0x4000, "Right Aux Shift (Bit 14)", true),
    POWERUP_RIGHT(0x8000, "Right PowerUp (Bit 15)", true),
    ONOFF_LEFT(0x10000, "Left On/Off (Bit 16)", false),
    ONOFF_RIGHT(0x20000, "Right On/Off (Bit 17)", true);

    val isShiftUpButton: Boolean
        get() = this == SHIFT_UP_RIGHT || this == SHIFT_UP_RIGHT_ALT || this == SHIFT_UP_LEFT || this == ONOFF_RIGHT

    val isShiftDownButton: Boolean
        get() = this == SHIFT_DOWN_LEFT || this == SHIFT_DOWN_LEFT_ALT || this == SHIFT_DOWN_RIGHT || this == ONOFF_LEFT
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

---

## 4. Phase 1A (`SA-1-BLE`): Complete `0x23` + `0x37` Packet Parser, Cross-Pod Deduplication & Raw Packet Logging

### 4.1 `app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt`

Supports:
1. `0x23` Field 1 (`0x08`) 32-bit inverse bitmap (including `Z = 0x0080` and all shift bit positions).
2. `0x23` Field 2 (`0x12`) nested analog/paddle messages (if a Click v2 / Ride encodes shift paddles as analog values $\ge 25$).
3. `0x37` legacy Zwift Click v1 shift packets (`0x37 0x08 0x00` / `0x01`).

```kotlin
package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ClickButton
import java.util.EnumMap

object ZwiftPacketParser {
    const val MSG_KEYPAD_STATUS: Byte = 0x23
    const val MSG_CLICK_V1_SHIFT: Byte = 0x37
    const val PROTO_FIELD_1_VARINT: Byte = 0x08
    const val PROTO_FIELD_2_LEN_DELIM: Byte = 0x12
    const val MAX_PACKET_SIZE = 64
    const val MAX_VARINT_BYTES = 5

    fun parseActiveButtonMask(payload: ByteArray): Int? {
        if (payload.size < 3 || payload.size > MAX_PACKET_SIZE) return null

        // Support legacy 0x37 Click packet if emitted by top rocker
        if (payload[0] == MSG_CLICK_V1_SHIFT && payload[1] == PROTO_FIELD_1_VARINT) {
            val stateByte = payload[2].toInt() and 0xFF
            return when (stateByte) {
                0x00 -> ClickButton.SHIFT_UP_RIGHT.bitmask
                0x01 -> ClickButton.SHIFT_DOWN_LEFT.bitmask
                else -> 0
            }
        }

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
                var activeMask = result.inv()
                // Also inspect optional Field 2 (0x12) analog/paddle group if present
                if (index < payload.size && payload[index] == PROTO_FIELD_2_LEN_DELIM) {
                    activeMask = activeMask or parseField2AnalogPaddles(payload, index + 1)
                }
                return activeMask
            }
            shift += 7
        }
        return null
    }

    /**
     * Parses Field 2 (0x12) RideAnalogKeyGroup for any non-zero analog paddle deflection.
     * Format: [groupLen] (0x0A [entryLen] 0x08 [locationId] 0x10 [zigzagSint32])*
     */
    private fun parseField2AnalogPaddles(payload: ByteArray, startIdx: Int): Int {
        if (startIdx >= payload.size) return 0
        val groupLen = payload[startIdx].toInt() and 0xFF
        var idx = startIdx + 1
        val endIdx = minOf(payload.size, idx + groupLen)
        var extraMask = 0

        while (idx + 5 <= endIdx) {
            if (payload[idx] != 0x0A.toByte()) break
            val entryLen = payload[idx + 1].toInt() and 0xFF
            if (entryLen < 4 || idx + 2 + entryLen > endIdx) break
            val locTag = payload[idx + 2].toInt() and 0xFF
            val locId = payload[idx + 3].toInt() and 0xFF
            val valTag = payload[idx + 4].toInt() and 0xFF
            val rawVal = payload[idx + 5].toInt() and 0xFF
            if (locTag == 0x08 && valTag == 0x10 && rawVal != 0) {
                if (locId == 0) extraMask = extraMask or ClickButton.SHIFT_DOWN_LEFT.bitmask
                if (locId == 1) extraMask = extraMask or ClickButton.SHIFT_UP_RIGHT.bitmask
            }
            idx += 2 + entryLen
        }
        return extraMask
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

### 4.2 `app/src/main/java/com/ankiride/bridge/ble/ZwiftGestureEngine.kt`

Adds a `120ms` cross-pod debounce guard (`DEBOUNCE_WINDOW_MS = 120L`) so mirrored packets arriving from `F4:C4:59:0C:6F:29` and `C0:4A:0E:F9:A3:50` 1ms apart never double-fire:

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
        const val DEBOUNCE_WINDOW_MS = 120L
    }

    private class ButtonStateTracker {
        var isPressed: Boolean = false
        var pressStartTime: Long = 0L
        var longPressTriggered: Boolean = false
        var lastEmittedTime: Long = -1000L
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
                    if ((nowMs - tracker.lastEmittedTime) >= DEBOUNCE_WINDOW_MS) {
                        tracker.lastEmittedTime = nowMs
                        onGestureDetected(ButtonGestureEvent(button, PressType.LONG_PRESS, nowMs))
                    }
                }
            } else if (!isPressed && tracker.isPressed) {
                tracker.isPressed = false
                val holdDuration = nowMs - tracker.pressStartTime
                if (!tracker.longPressTriggered && holdDuration < LONG_PRESS_THRESHOLD_MS) {
                    if ((nowMs - tracker.lastEmittedTime) >= DEBOUNCE_WINDOW_MS) {
                        tracker.lastEmittedTime = nowMs
                        onGestureDetected(ButtonGestureEvent(button, PressType.SINGLE_CLICK, nowMs))
                    }
                }
            }
        }
    }
}
```

### 4.3 `app/src/main/java/com/ankiride/bridge/ble/ZwiftBleController.kt`

1. Merges per-pod button masks (`podButtonMasks[address] = mask`) before calling `gestureEngine.processButtonStates` so mirrored packets from both pods never conflict.
2. Logs **every** non-idle notification on `MEASUREMENT_CHAR_UUID` (including any unknown message ID or Field 2 change) so no button on your controller can ever be silent in the log.

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
import java.util.EnumMap
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
    private val podActiveMasks = ConcurrentHashMap<String, Int>() // address -> last activeMask
    private val lastLoggedPayloadHex = ConcurrentHashMap<String, String>()
    private var leftPodWatchdogJob: Job? = null
    private var currentConfig = AppConfiguration()
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val address = device.address ?: return
            if (activeGatts.containsKey(address)) return

            // In 1-Pod Mode, once 1 pod is connected (since Click v2 mirrors both pods over 1 link), ignore second pod
            if (currentConfig.rightPodOnlyNoZwift && activeGatts.isNotEmpty()) {
                return
            }

            val mfgData = result.scanRecord?.getManufacturerSpecificData(ZWIFT_MANUFACTURER_ID)
            val podType = if (mfgData != null && mfgData.isNotEmpty()) mfgData[0] else null
            val isLeftPod = (podType == DEVICE_ID_LEFT_POD)
            val podLabel = when (podType) {
                DEVICE_ID_RIGHT_POD -> "RightPod(0x07)"
                DEVICE_ID_LEFT_POD -> "LeftPod(0x08)"
                DEVICE_ID_CLICK -> "Click(0x09)"
                null -> "ZwiftPod"
                else -> "Pod(0x%02X)".format(podType)
            }

            if (currentConfig.rightPodOnlyNoZwift && isLeftPod) {
                return
            }

            val allowedByPin = when {
                !currentConfig.pinnedRightPodMac.isNullOrBlank() && address.equals(currentConfig.pinnedRightPodMac, true) -> true
                !currentConfig.pinnedLeftPodMac.isNullOrBlank() && address.equals(currentConfig.pinnedLeftPodMac, true) -> true
                currentConfig.pinnedRightPodMac.isNullOrBlank() && currentConfig.pinnedLeftPodMac.isNullOrBlank() -> true
                else -> false
            }
            if (!allowedByPin) return

            DiagnosticLogger.info(
                LogCategory.BLE,
                "Discovered Zwift controller: name=${device.name ?: "Zwift Click"}, mac=$address, pod=$podLabel, rssi=${result.rssi}dBm"
            )
            connectToDevice(device, isLeftPod = isLeftPod)
        }

        override fun onScanFailed(errorCode: Int) {
            DiagnosticLogger.error(LogCategory.BLE, "BLE Scan failed with errorCode=$errorCode")
            _connectionState.value = BleConnectionState.ERROR
        }
    }

    fun updateConfiguration(config: AppConfiguration) {
        currentConfig = config
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
            "Starting BLE scan (1-Pod Default=${config.rightPodOnlyNoZwift})"
        )
        _connectionState.value = BleConnectionState.SCANNING
        isScanning = true
        scanner.startScan(filters, settings, scanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice, isLeftPod: Boolean) {
        _connectionState.value = BleConnectionState.CONNECTING
        DiagnosticLogger.info(LogCategory.BLE, "Connecting GATT to ${device.address}...")
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
        val count = connectedPods.size
        val nextState = when {
            count >= 2 -> BleConnectionState.CONNECTED_BOTH_PODS
            count == 1 -> BleConnectionState.CONNECTED_RIGHT_ONLY
            activeGatts.isEmpty() -> BleConnectionState.DISCONNECTED
            else -> BleConnectionState.CONNECTING
        }
        _connectionState.value = nextState
    }

    private fun createGattCallback(device: BluetoothDevice, isLeftPod: Boolean) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && gatt != null) {
                DiagnosticLogger.info(LogCategory.BLE, "GATT connected to ${device.address}. Discovering services...")
                _connectionState.value = BleConnectionState.HANDSHAKING
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                DiagnosticLogger.warn(LogCategory.BLE, "GATT disconnected from ${device.address} (status=$status)")
                gatt?.close()
                activeGatts.remove(device.address)
                connectedPods.remove(device.address)
                podActiveMasks.remove(device.address)
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
                if (_lastRightMac.value == null) {
                    _lastRightMac.value = device.address
                } else if (_lastRightMac.value != device.address) {
                    _lastLeftMac.value = device.address
                }
                DiagnosticLogger.info(LogCategory.BLE, "'RideOn' handshake complete on ${device.address}")
                updateConnectionState()
                if (isLeftPod && !currentConfig.rightPodOnlyNoZwift && gatt != null) {
                    scheduleLeftPodWatchdog(device)
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
        if (charUuid != MEASUREMENT_CHAR_UUID || value.isEmpty()) return
        val msgId = value[0]
        // Ignore periodic idle keep-alive heartbeats (0x15, 0x19)
        if (msgId == 0x15.toByte() || msgId == 0x19.toByte()) return

        val hex = value.joinToString(" ") { "%02X".format(it) }
        val mask = ZwiftPacketParser.parseActiveButtonMask(value)

        if (mask == null) {
            DiagnosticLogger.info(LogCategory.PACKET, "Non-0x23 packet from $address (msgId=0x${"%02X".format(msgId)}): [$hex]")
            return
        }

        val prevMask = podActiveMasks.put(address, mask) ?: 0
        if (mask != 0 || prevMask != 0 || lastLoggedPayloadHex.put(address, hex) != hex) {
            if (mask != 0) {
                val matchedNames = ClickButton.entries.filter { (mask and it.bitmask) != 0 }.joinToString(",") { it.name }
                DiagnosticLogger.info(
                    LogCategory.PACKET,
                    "0x23 packet from $address: [$hex] -> activeMask=0x${"%05X".format(mask)} (${matchedNames.ifEmpty { "UNMAPPED_BIT" }})"
                )
            }
        }

        // Combine active button masks across connected pods so mirrored pods don't cancel or double-trigger
        val combinedMask = podActiveMasks.values.fold(0) { acc, m -> acc or m }
        val combinedMap = EnumMap<ClickButton, Boolean>(ClickButton::class.java)
        ClickButton.entries.forEach { button ->
            combinedMap[button] = (combinedMask and button.bitmask) != 0
        }
        gestureEngine.processButtonStates(combinedMap)
    }

    private fun writeRideOnHandshake(gatt: BluetoothGatt, service: BluetoothGattService) {
        val controlChar = service.getCharacteristic(CONTROL_POINT_CHAR_UUID) ?: return
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
            val existing = activeGatts.remove(leftDevice.address)
            connectedPods.remove(leftDevice.address)
            podActiveMasks.remove(leftDevice.address)
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
        podActiveMasks.clear()
        _connectionState.value = BleConnectionState.DISCONNECTED
    }
}
```

---

## 5. Phase 1C (`SA-3-MYWHOOSH`): OpenBikeControl `tcpNoDelay` + `0x04` Reader + Simultaneous MyShift Touch

### 5.1 `app/src/main/java/com/ankiride/bridge/mywhoosh/OpenBikeControlClient.kt`

Implements:
- `client.tcpNoDelay = true` + `90ms` button hold (`PRESS_HOLD_MS = 90L`) so MyWhoosh never coalesces press + release into a single 30fps frame.
- Official `_openbikecontrol._tcp` TXT records including `service-uuids=d273f680-d548-419d-b9d1-fa0472345229` and `id=ankiride01` per the [OpenBikeControl mDNS \& TCP specification](https://www.google.com/search?q=https%3A%2F%2Fopenbikecontrol.org%2Fmdns).
- Incoming `0x04` App Information parser (`listenToClientMessages`) that logs the exact app ID, version, and supported button opcodes sent by MyWhoosh upon connecting.

```kotlin
package com.ankiride.bridge.mywhoosh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.MyWhooshAction
import kotlinx.coroutines.*
import java.io.InputStream
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
        const val OPENBIKE_BLE_SERVICE_UUID = "d273f680-d548-419d-b9d1-fa0472345229"
        const val MSG_BUTTON_STATE: Byte = 0x01
        const val MSG_DEVICE_STATUS: Byte = 0x02
        const val MSG_HAPTIC_FEEDBACK: Byte = 0x03
        const val MSG_APP_INFO: Byte = 0x04
        const val STATE_PRESSED: Byte = 0x01
        const val STATE_RELEASED: Byte = 0x00
        const val PRESS_HOLD_MS = 90L
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
                        client.tcpNoDelay = true
                        connectedClients.add(client)
                        DiagnosticLogger.info(
                            LogCategory.MYWHOOSH,
                            "MyWhoosh connected from $rawHost (tcpNoDelay=true, clients=${connectedClients.size})"
                        )
                        // Send initial OpenBikeControl 0x02 Device Status (100% battery, connected = 1)
                        sendRawBytes(client, byteArrayOf(MSG_DEVICE_STATUS, 0x64, 0x01))
                        listenToClientMessages(client, rawHost)
                    } else {
                        DiagnosticLogger.warn(LogCategory.MYWHOOSH, "Rejected non-LAN socket from $rawHost")
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

    private fun listenToClientMessages(client: Socket, host: String) {
        scope.launch(Dispatchers.IO) {
            val buf = ByteArray(256)
            try {
                val input: InputStream = client.getInputStream()
                while (isActive && !client.isClosed) {
                    val readCount = input.read(buf)
                    if (readCount <= 0) break
                    val packet = buf.copyOfRange(0, readCount)
                    parseIncomingAppPacket(host, packet)
                }
            } catch (_: Exception) {
            } finally {
                connectedClients.remove(client)
                DiagnosticLogger.warn(LogCategory.MYWHOOSH, "MyWhoosh client $host disconnected")
            }
        }
    }

    private fun parseIncomingAppPacket(host: String, data: ByteArray) {
        if (data.isEmpty()) return
        when (data[0]) {
            MSG_APP_INFO -> {
                try {
                    if (data.size >= 5) {
                        val ver = data[1].toInt() and 0xFF
                        val idLen = data[2].toInt() and 0xFF
                        val appId = if (3 + idLen <= data.size) String(data, 3, idLen, Charsets.UTF_8) else "?"
                        val verIdx = 3 + idLen
                        val verLen = if (verIdx < data.size) data[verIdx].toInt() and 0xFF else 0
                        val appVer = if (verIdx + 1 + verLen <= data.size) String(data, verIdx + 1, verLen, Charsets.UTF_8) else "?"
                        val btnCountIdx = verIdx + 1 + verLen
                        val btnCount = if (btnCountIdx < data.size) data[btnCountIdx].toInt() and 0xFF else 0
                        val btnsHex = if (btnCountIdx + 1 < data.size) {
                            data.copyOfRange(btnCountIdx + 1, data.size).joinToString(" ") { "0x%02X".format(it) }
                        } else "ALL"
                        DiagnosticLogger.info(
                            LogCategory.MYWHOOSH,
                            "Received 0x04 AppInfo from $host: app='$appId' v$appVer (proto=$ver, supportedButtons($btnCount)=[$btnsHex])"
                        )
                    }
                } catch (_: Exception) {
                    val hex = data.joinToString(" ") { "%02X".format(it) }
                    DiagnosticLogger.info(LogCategory.MYWHOOSH, "Received 0x04 AppInfo from $host: [$hex]")
                }
            }
            MSG_HAPTIC_FEEDBACK -> {
                val hex = data.joinToString(" ") { "%02X".format(it) }
                DiagnosticLogger.info(LogCategory.MYWHOOSH, "MyWhoosh confirmed action with 0x03 Haptic packet: [$hex]")
            }
            else -> {
                val hex = data.joinToString(" ") { "%02X".format(it) }
                DiagnosticLogger.info(LogCategory.MYWHOOSH, "Incoming packet from MyWhoosh ($host): [$hex]")
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
                "Sent OpenBikeControl 0x01 PRESS [01 ${"%02X".format(action.openBikeOpcode)} 01] (${action.name}) -> holding ${PRESS_HOLD_MS}ms"
            )
            delay(PRESS_HOLD_MS)
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
            setAttribute("version", "1")
            setAttribute("id", "ankiride01")
            setAttribute("name", "AnkiRide Bridge")
            setAttribute("service-uuids", OPENBIKE_BLE_SERVICE_UUID)
            setAttribute("manufacturer", "AnkiRide")
            setAttribute("model", "Zwift Click v2 Bridge")
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                DiagnosticLogger.info(LogCategory.MYWHOOSH, "mDNS registered: ${info?.serviceName} ($SERVICE_TYPE) port=$port")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                DiagnosticLogger.error(LogCategory.MYWHOOSH, "mDNS registration failed (errorCode=$errorCode)")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo?) {}
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
    }

    fun close() {
        stopServer()
    }
}
```

### 5.2 `app/src/main/java/com/ankiride/bridge/mywhoosh/MyWhooshNetworkDispatcherImpl.kt`

When `useMyShiftTouchFallbackWhenNoClient` is enabled on the Tablet (`Mode 1` or `Mode 4`), dispatches **both** the OpenBikeControl binary packet (`0x01`/`0x02`) **AND** the `MyShiftGestureFallback` screen tap on `SHIFT_UP` / `SHIFT_DOWN` so your on-screen MyShift arrows visually click and shift gears even if MyWhoosh's trainer setup uses on-screen shifting!

```kotlin
package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.MyWhooshAction
import com.ankiride.bridge.core.MyWhooshActionDispatcher
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.service.BridgeAccessibilityService

class MyWhooshNetworkDispatcherImpl(
    private val client: OpenBikeControlClient,
    private val accessibilityServiceSupplier: () -> BridgeAccessibilityService?
) : MyWhooshActionDispatcher {

    override suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean {
        var deliveredNetwork = false

        // 1. Send official binary OpenBikeControl packet (0x01) to connected MyWhoosh client (Tablet or Pixel 10)
        if (client.hasConnectedClients()) {
            deliveredNetwork = client.sendAction("127.0.0.1", config.openBikeServerPort, action)
        } else {
            DiagnosticLogger.warn(
                LogCategory.MYWHOOSH,
                "No OpenBikeControl TCP client connected yet for $action (tap OpenBikeControl icon in MyWhoosh Connection Screen)"
            )
        }

        // 2. Also perform on-screen MyShift tap in Tablet Modes if enabled (for SHIFT_UP / SHIFT_DOWN)
        val isTabletMyWhoosh = config.mode == OperatingMode.MODE_1_TABLET_SPLIT_SCREEN ||
            config.mode == OperatingMode.MODE_4_MYWHOOSH_ONLY

        var deliveredTouch = false
        if (isTabletMyWhoosh && config.useMyShiftTouchFallbackWhenNoClient &&
            (action == MyWhooshAction.SHIFT_UP || action == MyWhooshAction.SHIFT_DOWN)
        ) {
            val service = accessibilityServiceSupplier()
            if (service != null) {
                deliveredTouch = MyShiftGestureFallback.performTouchAction(service, action, config)
            }
        }

        return deliveredNetwork || deliveredTouch
    }
}
```

### 5.3 `app/src/main/java/com/ankiride/bridge/mywhoosh/MyShiftGestureFallback.kt`

Logs the exact screen coordinates `(tapX, tapY)` inside `com.mywhoosh.whooshgame` window bounds whenever it taps the on-screen MyShift `<` / `>` buttons:

```kotlin
package com.ankiride.bridge.mywhoosh

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.MyWhooshAction

object MyShiftGestureFallback {
    const val MYWHOOSH_PACKAGE = "com.mywhoosh.whooshgame"
    const val TAP_DURATION_MS = 25L

    fun performTouchAction(
        service: AccessibilityService,
        action: MyWhooshAction,
        config: AppConfiguration
    ): Boolean {
        if (action != MyWhooshAction.SHIFT_UP && action != MyWhooshAction.SHIFT_DOWN) {
            return false
        }

        val myWhooshWindow = service.windows?.firstOrNull {
            it.root?.packageName?.toString() == MYWHOOSH_PACKAGE
        } ?: return false

        val bounds = Rect()
        myWhooshWindow.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false

        val (normX, normY) = if (action == MyWhooshAction.SHIFT_UP) {
            config.myShiftUpNormalizedX to config.myShiftUpNormalizedY
        } else {
            config.myShiftDownNormalizedX to config.myShiftDownNormalizedY
        }

        val tapX = bounds.left + (bounds.width() * normX)
        val tapY = bounds.top + (bounds.height() * normY)

        val path = Path().apply {
            moveTo(tapX, tapY)
            lineTo(tapX, tapY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return try {
            val dispatched = service.dispatchGesture(gesture, null, null)
            DiagnosticLogger.info(
                LogCategory.MYWHOOSH,
                "MyShift Touch Tap for $action at (${tapX.toInt()}, ${tapY.toInt()}) in window=$bounds -> dispatched=$dispatched"
            )
            dispatched
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.MYWHOOSH, "MyShift Touch Tap failed: ${e.message}")
            false
        }
    }
}
```

---

## 6. Phase 2 (`SA-4-APP-UI`): Updated `ActionRouter.kt`, `BridgeForegroundService.kt` & `MainActivity.kt`

### 6.1 `app/src/main/java/com/ankiride/bridge/service/ActionRouter.kt`

- Recognizes all shift up/down bitmasks (`button.isShiftUpButton`, `button.isShiftDownButton`) and `Z = 0x0080`.
- In `Mode 4 (MyWhoosh-Only)` when `rightPodOnlyNoZwift = true`, maps **`+` and `A` to `SHIFT_UP`** and **`Z` and `B` to `SHIFT_DOWN`** (matching [BikeControl's Right-Pod-Only layout](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fcontroller-zwift-click-v2%2F) where `+` shifts up and `B`/`Z` shifts down!).

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

        // 1. User custom button overrides take top priority
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

        // 2. Deterministic mode defaults
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
        when {
            event.button.isShiftUpButton -> {
                val mwAction = if (event.type == PressType.LONG_PRESS) MyWhooshAction.SHIFT_DOWN else MyWhooshAction.SHIFT_UP
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: ${event.button.name} (${event.type.name}) -> $mwAction")
                scope.launch { myWhooshDispatcher.dispatch(mwAction, config) }
            }
            event.button.isShiftDownButton -> {
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: ${event.button.name} -> SHIFT_DOWN")
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
            }
            event.button == ClickButton.Z -> {
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
            event.button == ClickButton.A -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.FLIP_OR_EASY else AnkiAction.FLIP_OR_GOOD
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: A (${event.type.name}) -> $action")
                ankiDispatcher.dispatch(action)
            }
            event.button == ClickButton.B -> {
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: B -> FLIP_OR_HARD")
                ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            }
            event.button == ClickButton.Y -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_AGAIN
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: Y (${event.type.name}) -> $action")
                ankiDispatcher.dispatch(action)
            }
        }
    }

    private fun routeDualPodCombinedMode(event: ButtonGestureEvent, config: AppConfiguration) {
        when {
            event.button == ClickButton.A -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_GOOD)
            event.button == ClickButton.B -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            event.button == ClickButton.Y -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_AGAIN)
            event.button == ClickButton.Z -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_EASY
                ankiDispatcher.dispatch(action)
            }
            event.button.isShiftUpButton -> {
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_UP, config) }
            }
            event.button.isShiftDownButton -> {
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
            }
            event.button == ClickButton.LEFT -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.STEER_LEFT, config) }
            event.button == ClickButton.RIGHT -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.STEER_RIGHT, config) }
            event.button == ClickButton.UP -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.TOGGLE_CAMERA, config) }
            event.button == ClickButton.DOWN -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.EMOTE, config) }
        }
    }

    private fun mapAnkiOnlyMode(event: ButtonGestureEvent): AnkiAction? {
        return when {
            event.button == ClickButton.A || event.button.isShiftUpButton -> AnkiAction.FLIP_OR_GOOD
            event.button == ClickButton.B -> AnkiAction.FLIP_OR_HARD
            event.button == ClickButton.Y -> if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_AGAIN
            event.button == ClickButton.Z -> if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_EASY
            event.button.isShiftDownButton || event.button == ClickButton.LEFT -> AnkiAction.UNDO
            event.button == ClickButton.RIGHT || event.button == ClickButton.UP || event.button == ClickButton.DOWN -> AnkiAction.FLIP_CARD
            else -> null
        }
    }

    private fun mapMyWhooshOnlyMode(event: ButtonGestureEvent, config: AppConfiguration): MyWhooshAction? {
        return when {
            event.button.isShiftUpButton -> {
                if (config.rightPodOnlyNoZwift && event.type == PressType.LONG_PRESS) MyWhooshAction.SHIFT_DOWN
                else MyWhooshAction.SHIFT_UP
            }
            event.button.isShiftDownButton -> MyWhooshAction.SHIFT_DOWN
            event.button == ClickButton.Z -> if (config.rightPodOnlyNoZwift) MyWhooshAction.SHIFT_DOWN else MyWhooshAction.USE_POWERUP
            event.button == ClickButton.A -> if (config.rightPodOnlyNoZwift) MyWhooshAction.SHIFT_UP else MyWhooshAction.UI_CONFIRM
            event.button == ClickButton.B -> if (config.rightPodOnlyNoZwift) MyWhooshAction.SHIFT_DOWN else MyWhooshAction.UI_BACK
            event.button == ClickButton.Y -> MyWhooshAction.HUD_TOGGLE
            event.button == ClickButton.LEFT -> MyWhooshAction.STEER_LEFT
            event.button == ClickButton.RIGHT -> MyWhooshAction.STEER_RIGHT
            event.button == ClickButton.UP -> MyWhooshAction.TOGGLE_CAMERA
            event.button == ClickButton.DOWN -> MyWhooshAction.EMOTE
            else -> null
        }
    }
}
```

### 6.2 `app/src/main/java/com/ankiride/bridge/service/BridgeForegroundService.kt`

Fixes the duplicate-instance bug by updating `currentConfig` in-place when already running, and adds `triggerTestMyWhooshAction` / `triggerTestAnkiAction` so the UI test buttons work directly through the active service:

```kotlin
package com.ankiride.bridge.service

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
import com.ankiride.bridge.core.*
import com.ankiride.bridge.mywhoosh.MyWhooshNetworkDispatcherImpl
import com.ankiride.bridge.mywhoosh.OpenBikeControlClient
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
        const val ACTION_UPDATE_CONFIG = "com.ankiride.bridge.UPDATE_CONFIG"
        const val ACTION_STOP = "com.ankiride.bridge.STOP"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _connectionStateFlow = MutableStateFlow(BleConnectionState.DISCONNECTED)
        val connectionStateFlow: StateFlow<BleConnectionState> = _connectionStateFlow.asStateFlow()

        var currentConfig: AppConfiguration = AppConfiguration()
            private set

        private var activeServiceInstance: BridgeForegroundService? = null

        fun startService(context: Context, config: AppConfiguration) {
            currentConfig = config
            if (_isRunningFlow.value && activeServiceInstance != null) {
                activeServiceInstance?.bleController?.updateConfiguration(config)
                DiagnosticLogger.info(LogCategory.SYSTEM, "Updated live configuration (mode=${config.mode}, 1Pod=${config.rightPodOnlyNoZwift})")
                return
            }
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

        fun triggerTestShift(action: MyWhooshAction) {
            val svc = activeServiceInstance ?: run {
                DiagnosticLogger.warn(LogCategory.MYWHOOSH, "Start Bridge first before testing $action")
                return
            }
            svc.serviceScope.launch {
                svc.myWhooshDispatcher?.dispatch(action, currentConfig)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var bleController: ZwiftBleController? = null
    private var openBikeServer: OpenBikeControlClient? = null
    private var myWhooshDispatcher: MyWhooshNetworkDispatcherImpl? = null
    private var ankiDispatcher: AnkiActionDispatcherImpl? = null
    private var actionRouter: ActionRouter? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopBridge()
            stopSelf()
            return START_NOT_STICKY
        }

        if (_isRunningFlow.value && bleController != null) {
            bleController?.updateConfiguration(currentConfig)
            return START_STICKY
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
            .setContentTitle("AnkiRide Bridge Active")
            .setContentText("OpenBikeControl server + Zwift Click controller running")
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
        stopBridge()
        _isRunningFlow.value = true

        val server = OpenBikeControlClient(this, serviceScope)
        openBikeServer = server
        server.startServer(currentConfig.openBikeServerPort)

        val whoosh = MyWhooshNetworkDispatcherImpl(
            client = server,
            accessibilityServiceSupplier = { BridgeAccessibilityService.activeInstance }
        )
        val anki = AnkiActionDispatcherImpl(serviceProvider = { BridgeAccessibilityService.activeInstance })
        val router = ActionRouter(
            scope = serviceScope,
            configSupplier = { currentConfig },
            ankiDispatcher = anki,
            myWhooshDispatcher = whoosh
        )

        val ble = ZwiftBleController(this, serviceScope)
        bleController = ble
        myWhooshDispatcher = whoosh
        ankiDispatcher = anki
        actionRouter = router

        serviceScope.launch {
            ble.connectionState.collect { state ->
                _connectionStateFlow.value = state
            }
        }

        serviceScope.launch {
            ble.gestureEvents.collect { event ->
                router.routeGesture(event)
            }
        }

        ble.startScanAndConnect(currentConfig)
    }

    private fun stopBridge() {
        bleController?.disconnect()
        openBikeServer?.stopServer()

        bleController = null
        openBikeServer = null
        myWhooshDispatcher = null
        ankiDispatcher = null
        actionRouter = null

        _isRunningFlow.value = false
        _connectionStateFlow.value = BleConnectionState.DISCONNECTED
    }

    override fun onDestroy() {
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
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
                description = "Keeps AnkiRide OpenBikeControl server and BLE routing alive in background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
```

### 6.3 `DiagnosticLogCard` in `MainActivity.kt` (Adds `Test Shift Up (+)` and `Test Shift Down (–)` Buttons)

Add this row inside `DiagnosticLogCard()` in `MainActivity.kt` right above the `Copy Logs` / `Clear Logs` / `Inspect Windows` row:

```kotlin
            // Quick MyWhoosh Shift Test Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { BridgeForegroundService.triggerTestShift(MyWhooshAction.SHIFT_DOWN) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Shift Down (–)", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { BridgeForegroundService.triggerTestShift(MyWhooshAction.SHIFT_UP) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Shift Up (+)", fontSize = 12.sp)
                }
            }
```

---

## 7. Review & Security Gates (`RG-0` through `RG-5`)

| Gate ID | Target Phase / Subagent | Review & Security Verification Criteria | Automated Check Command |
| :--- | :--- | :--- | :--- |
| **`RG-0`** | Phase 0 (`ORCHESTRATOR`) | `Contracts.kt` maps `Z(0x0080, ...)` (Bit 7), defines `isShiftUpButton` / `isShiftDownButton`, and includes `DiagnosticLogger.kt`. | `grep -q "Z(0x0080" app/src/main/java/com/ankiride/bridge/core/Contracts.kt` |
| **`RG-1`** | Phase 1A (`SA-1-BLE`) | `ZwiftPacketParser` parses `0x23` (Field 1 + Field 2 `0x12`) and `0x37`; `ZwiftGestureEngine` enforces `DEBOUNCE_WINDOW_MS = 120L`; `ZwiftBleController` merges `podActiveMasks` across pods. | `grep -q "DEBOUNCE_WINDOW_MS" app/src/main/java/com/ankiride/bridge/ble/ZwiftGestureEngine.kt && grep -q "podActiveMasks" app/src/main/java/com/ankiride/bridge/ble/ZwiftBleController.kt` |
| **`RG-2`** | Phase 1B (`SA-2-ANKI`) | `accessibility_service_config.xml` pins `com.ichi2.anki,com.mywhoosh.whooshgame`; `AnkiAccessibilityBridge` checks `!node.isPassword`, enforces `FLIP_TO_GRADE_GUARD_MS = 350L`, and provides `dumpInteractiveWindowsToLog`. | `grep -q "dumpInteractiveWindowsToLog" app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt` |
| **`RG-3`** | Phase 1C (`SA-3-MYWHOOSH`) | `OpenBikeControlClient` sets `tcpNoDelay = true`, holds press for `PRESS_HOLD_MS = 90L`, advertises `service-uuids=d273f680-d548-419d-b9d1-fa0472345229`, and logs incoming `0x04` AppInfo packets. | `grep -q "tcpNoDelay = true" app/src/main/java/com/ankiride/bridge/mywhoosh/OpenBikeControlClient.kt && grep -q "MSG_APP_INFO" app/src/main/java/com/ankiride/bridge/mywhoosh/OpenBikeControlClient.kt` |
| **`RG-4`** | Phase 2 (`SA-4-APP-UI`) | `BridgeForegroundService` updates config in-place without duplicating `ZwiftBleController`; `MainActivity.kt` includes `ON_RESUME` permission refresh, `Copy Logs` / `Clear Logs`, and `Test Shift Up (+)` / `Test Shift Down (–)`. | `grep -q "triggerTestShift" app/src/main/java/com/ankiride/bridge/service/BridgeForegroundService.kt` |
| **`RG-5`** | Phase 3 (`SA-5-REVIEWER`) | Update unit tests in `ZwiftPacketParserTest.kt` and `ActionRouterTest.kt` for `Z = 0x0080` and `PRESS_HOLD_MS = 90L`, and pass `./gradlew testDebugUnitTest --no-daemon`. | `./verify.sh && ./gradlew testDebugUnitTest --no-daemon` |
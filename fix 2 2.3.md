# Multi-Agent Phase Plan & Review-Gated Specification: `AnkiMywhooshControl` v2.3 (Right-Pod 4-Color Split: 2 Shift + 2 Simple Anki & Per-Mode Custom Button UI)

## 1. Executive Summary of Changes in v2.3

This specification upgrades `AnkiMywhooshControl` (`com.ankiride.bridge`) to directly implement your **Right-Pod 4-Colorful-Button Workflow** (2 buttons for **Shift Up / Shift Down** + 2 buttons for **Simple Anki "Didn't Know (Again)" / "Good"**) and adds a **Per-Mode Persistent Visual Button Customizer UI**:

1. **Right-Pod 4-Colorful-Button Default Split (Zero Top-Rocker Dependency)**:
   - Your tablet diagnostic log proved that all 4 colorful diamond buttons on the Right Pod—**`Z` (`0x0080`, Top Orange)**, **`A` (`0x0010`, Bottom Blue)**, **`Y` (`0x0040`, Left Yellow)**, and **`B` (`0x0020`, Right Green)**—fire 100% reliably.
   - Instead of wasting 3 colorful buttons on `Again / Hard / Good` and relying on the top `+` rocker for `Shift Up`, v2.3 splits the **4 colorful buttons 2-and-2** in combined modes (`Mode 1: Tablet Split-Screen` and `Mode 2: Tablet Anki + Phone MyWhoosh`):
     - **2 Buttons for Shifting**:
       - **`Z` (Top Orange, `0x0080`)** $\rightarrow$ **MyWhoosh: Shift Up (+)** *(Top button = harder gear)*
       - **`A` (Bottom Blue, `0x0010`)** $\rightarrow$ **MyWhoosh: Shift Down (–)** *(Bottom button = easier gear)*
     - **2 Buttons for Simple 2-Choice Anki (`Didn't Know` vs. `Good`)**:
       - **`Y` (Left Yellow, `0x0040`)** $\rightarrow$ **Anki: Flip Card / Didn't Know — Again (1)** *(Long-Press `Y` = **Undo Last Card**)*
       - **`B` (Right Green, `0x0020`)** $\rightarrow$ **Anki: Flip Card / Knew It — Good (3)** *(Long-Press `B` = **Undo Last Card**)*
   - Includes one-tap preset buttons in the UI so you can also swap to **Top/Right (`Z`/`B`) for Shifting + Left/Bottom (`Y`/`A`) for Anki** anytime.

2. **Per-Mode Independent & Persistent Button Customization (`ButtonMappingStore.kt`)**:
   - In v2.2, `customBindings` was global across all modes, only lived in RAM (lost on app restart), and displayed `"Default Mode Mapping"` instead of showing the actual action assigned to each button.
   - In v2.3:
     - Every `OperatingMode` (`MODE_1_TABLET_SPLIT_SCREEN`, `MODE_2_TABLET_ANKI_PHONE_MYWHOOSH`, `MODE_3_ANKI_ONLY`, `MODE_4_MYWHOOSH_ONLY`) has its **own independent button map** (`ModeButtonBindingKey(mode, button, pressType)`) saved permanently to `SharedPreferences`.
     - `ActionRouter.resolveEffectiveAction(mode, rightPodOnly, button, pressType, customBindings)` resolves the **exact active `MappedTargetAction`** for every button in every mode, so the UI always displays the real action name (`"MyWhoosh: Shift Up (+)"`, `"Anki: Didn't Know / Again (1)"`, etc.) alongside color-coded controller badges (`Z` Orange, `Y` Yellow, `B` Green, `A` Blue).

3. **Zero-Latency Split-Screen Touch Tap + Draggable Target Overlay (`MyShiftOverlayCalibrator.kt`)**:
   - Fires the `75ms` calibrated screen tap (`MyShiftGestureFallback`) **first with zero delay** in `Mode 1` and `Mode 4` (while sending the OpenBikeControl TCP packet concurrently in the background) so split-screen shifting never waits on network timeouts.

---

## 2. Default Per-Mode Button Matrix (Out-of-the-Box Defaults, 100% Customizable Per Mode)

| Right Pod Button | Bitmask | Gesture | **Modes 1 & 2 (Split-Screen / Dual Device)** | **Mode 3 (Anki-Only Study)** | **Mode 4 (MyWhoosh-Only Ride)** |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **🟠 `Z` (Top Orange)** | `0x0080` | Single Click | **MyWhoosh: Shift Up (+)** | Anki: Flip / Easy (4) | **MyWhoosh: Shift Up (+)** |
| **🟠 `Z` (Top Orange)** | `0x0080` | Long Press | **MyWhoosh: Shift Up (+)** | Anki: Undo Last Card | MyWhoosh: Toggle Camera |
| **🔵 `A` (Bottom Blue)** | `0x0010` | Single Click | **MyWhoosh: Shift Down (–)** | **Anki: Knew It / Good (3)** | **MyWhoosh: Shift Down (–)** |
| **🔵 `A` (Bottom Blue)** | `0x0010` | Long Press | **MyWhoosh: Shift Down (–)** | Anki: Flip / Easy (4) | MyWhoosh: Use Power-Up |
| **🟡 `Y` (Left Yellow)** | `0x0040` | Single Click | **Anki: Didn't Know / Again (1)** | **Anki: Didn't Know / Again (1)** | MyWhoosh: Steer Left |
| **🟡 `Y` (Left Yellow)** | `0x0040` | Long Press | **Anki: Undo Last Card** | **Anki: Undo Last Card** | MyWhoosh: Toggle HUD |
| **🟢 `B` (Right Green)** | `0x0020` | Single Click | **Anki: Knew It / Good (3)** | **Anki: Hard (2)** | MyWhoosh: Steer Right |
| **🟢 `B` (Right Green)** | `0x0020` | Long Press | **Anki: Undo Last Card** | **Anki: Undo Last Card** | MyWhoosh: Emote |
| **⚫ Top `+` Rocker** | `0x1000`/`0x2000` | Single / Long | **MyWhoosh: Shift Up (+) / Down (–)** | Anki: Knew It / Good (3) | **MyWhoosh: Shift Up (+) / Down (–)** |

*(Note: When `1-Controller Default` is turned OFF to enable the 2nd Left Pod, `Left – Rocker` also shifts down and `Left D-Pad` steers/controls MyWhoosh, all customizable per mode in the UI.)*

---

## 3. Multi-Agent Phase Topology & Write-Isolation Matrix

| Phase | Subagent ID | Role Description | Owned Repository Files (Read-Write) | Read-Only Dependencies | Blocking Gate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Phase 0** | `ORCHESTRATOR` | Shared Contracts (`ModeButtonBindingKey`, `ButtonMappingStore.kt`, `Z=0x0080`) & `DiagnosticLogger.kt` | `app/src/main/java/com/ankiride/bridge/core/Contracts.kt`, `app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt`, `app/src/main/java/com/ankiride/bridge/core/ButtonMappingStore.kt`, `verify.sh` | `build.gradle.kts`, `app/build.gradle.kts` | `RG-0` |
| **Phase 1A** | `SA-1-BLE` | Full `0x23` + `0x37` Parser, Cross-Pod Deduplication & Complete Packet Logger | `app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt`, `ZwiftGestureEngine.kt`, `ZwiftBleController.kt`, `app/src/test/java/com/ankiride/bridge/ble/*` | `core/*` | `RG-1` |
| **Phase 1B** | `SA-2-ANKI` | 3-Layer AnkiDroid Accessibility Engine + Multi-Window Dumper | `app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt`, `AnkiActionDispatcherImpl.kt`, `AnkiContentProviderHelper.kt`, `app/src/main/res/xml/accessibility_service_config.xml`, `app/src/test/java/com/ankiride/bridge/anki/*` | `core/*` | `RG-2` |
| **Phase 1C** | `SA-3-MYWHOOSH` | `MyShiftOverlayCalibrator` (`TYPE_ACCESSIBILITY_OVERLAY`), Zero-Latency `75ms` Split-Screen Tapper & `OpenBikeControlClient` | `app/src/main/java/com/ankiride/bridge/mywhoosh/MyShiftOverlayCalibrator.kt`, `MyShiftGestureFallback.kt`, `MyWhooshNetworkDispatcherImpl.kt`, `OpenBikeControlClient.kt`, `LanAddressValidator.kt`, `MyWhooshDiscovery.kt`, `app/src/main/res/xml/network_security_config.xml`, `app/src/test/java/com/ankiride/bridge/mywhoosh/*` | `core/*` | `RG-3` |
| **Phase 2** | `SA-4-APP-UI` | Per-Mode `ActionRouter`, Visual Color-Coded Button Customizer UI, Overlay Calibrator & Live Log UI | `app/src/main/java/com/ankiride/bridge/service/ActionRouter.kt`, `BridgeAccessibilityService.kt`, `BridgeForegroundService.kt`, `app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt`, `app/src/main/AndroidManifest.xml`, `app/src/test/java/com/ankiride/bridge/service/*` | `core/*`, `ble/*`, `anki/*`, `mywhoosh/*` | `RG-4` |
| **Phase 3** | `SA-5-REVIEWER` | Automated Gate Auditor & Unit Test Runner | Read-Only across entire repository (executes `./verify.sh` and `./gradlew testDebugUnitTest`) | Entire Repository | `RG-5` |

---

## 4. Phase 0 (`ORCHESTRATOR`): `Contracts.kt` & `ButtonMappingStore.kt`

### 4.1 `app/src/main/java/com/ankiride/bridge/core/Contracts.kt`

```kotlin
package com.ankiride.bridge.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class OperatingMode(val shortTitle: String, val subtitle: String) {
    MODE_1_TABLET_SPLIT_SCREEN(
        "Split-Screen",
        "MyWhoosh + AnkiDroid side-by-side on Tablet (Local Touch + 127.0.0.1)"
    ),
    MODE_2_TABLET_ANKI_PHONE_MYWHOOSH(
        "Tablet + Phone",
        "AnkiDroid on Tablet + MyWhoosh on Phone over Wi-Fi mDNS"
    ),
    MODE_3_ANKI_ONLY(
        "Anki-Only",
        "Full-screen AnkiDroid study mode (all buttons mapped to Anki)"
    ),
    MODE_4_MYWHOOSH_ONLY(
        "MyWhoosh-Only",
        "Full-screen MyWhoosh ride mode (all buttons mapped to MyWhoosh)"
    )
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

enum class ClickButton(
    val bitmask: Int,
    val displayName: String,
    val shortBadge: String,
    val badgeColorHex: Long,
    val isRightPod: Boolean,
    val isPrimaryConfigurable: Boolean = true
) {
    Z(0x0080, "Right 'Z' (Top Orange)", "Z", 0xFFE65100, true, true),
    A(0x0010, "Right 'A' (Bottom Blue)", "A", 0xFF1565C0, true, true),
    Y(0x0040, "Right 'Y' (Left Yellow)", "Y", 0xFFF9A825, true, true),
    B(0x0020, "Right 'B' (Right Green)", "B", 0xFF2E7D32, true, true),
    SHIFT_UP_RIGHT(0x1000, "Right Top '+' Rocker", "+", 0xFF455A64, true, true),
    SHIFT_DOWN_LEFT(0x0200, "Left Top '–' Rocker", "–", 0xFF455A64, false, true),
    LEFT(0x0001, "Left D-Pad ◀", "◀", 0xFF37474F, false, true),
    RIGHT(0x0004, "Left D-Pad ▶", "▶", 0xFF37474F, false, true),
    UP(0x0002, "Left D-Pad ▲", "▲", 0xFF37474F, false, true),
    DOWN(0x0008, "Left D-Pad ▼", "▼", 0xFF37474F, false, true),
    // Secondary bit aliases for hardware firmware variants (routed identically to primary rockers)
    SHIFT_UP_LEFT(0x0100, "Left Shift Up (Bit 8)", "+L", 0xFF455A64, false, false),
    SHIFT_DOWN_LEFT_ALT(0x0400, "Left '–' Rocker (Bit 10)", "–", 0xFF455A64, false, false),
    POWERUP_LEFT(0x0800, "Left PowerUp (Bit 11)", "P", 0xFF455A64, false, false),
    SHIFT_UP_RIGHT_ALT(0x2000, "Right '+' Rocker (Bit 13)", "+", 0xFF455A64, true, false),
    SHIFT_DOWN_RIGHT(0x4000, "Right Aux Shift (Bit 14)", "–R", 0xFF455A64, true, false),
    POWERUP_RIGHT(0x8000, "Right PowerUp (Bit 15)", "P", 0xFF455A64, true, false),
    ONOFF_LEFT(0x10000, "Left On/Off (Bit 16)", "O", 0xFF455A64, false, false),
    ONOFF_RIGHT(0x20000, "Right On/Off (Bit 17)", "O", 0xFF455A64, true, false);

    val canonicalButton: ClickButton
        get() = when (this) {
            SHIFT_UP_RIGHT_ALT, SHIFT_UP_LEFT, POWERUP_RIGHT, ONOFF_RIGHT -> SHIFT_UP_RIGHT
            SHIFT_DOWN_LEFT_ALT, SHIFT_DOWN_RIGHT, POWERUP_LEFT, ONOFF_LEFT -> SHIFT_DOWN_LEFT
            else -> this
        }
}

enum class PressType(val displayName: String) {
    SINGLE_CLICK("Single Press"),
    LONG_PRESS("Long Press (450ms)")
}

data class ButtonGestureEvent(
    val button: ClickButton,
    val type: PressType,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class AnkiAction(val displayName: String) {
    FLIP_OR_AGAIN("Anki: Didn't Know / Again (1)"),
    FLIP_OR_HARD("Anki: Hard (2)"),
    FLIP_OR_GOOD("Anki: Knew It / Good (3)"),
    FLIP_OR_EASY("Anki: Easy (4)"),
    FLIP_CARD("Anki: Flip Card Only"),
    UNDO("Anki: Undo Last Card")
}

enum class MyWhooshAction(val openBikeOpcode: Byte, val displayName: String) {
    SHIFT_UP(0x01, "MyWhoosh: Shift Up (+)"),
    SHIFT_DOWN(0x02, "MyWhoosh: Shift Down (–)"),
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
    NONE("Disabled (No Action)"),
    MW_SHIFT_UP("MyWhoosh: Shift Up (+)", myWhooshAction = MyWhooshAction.SHIFT_UP),
    MW_SHIFT_DOWN("MyWhoosh: Shift Down (–)", myWhooshAction = MyWhooshAction.SHIFT_DOWN),
    ANKI_FLIP_OR_AGAIN("Anki: Didn't Know / Again (1)", ankiAction = AnkiAction.FLIP_OR_AGAIN),
    ANKI_FLIP_OR_GOOD("Anki: Knew It / Good (3)", ankiAction = AnkiAction.FLIP_OR_GOOD),
    ANKI_UNDO("Anki: Undo Last Card", ankiAction = AnkiAction.UNDO),
    ANKI_FLIP_OR_HARD("Anki: Hard (2)", ankiAction = AnkiAction.FLIP_OR_HARD),
    ANKI_FLIP_OR_EASY("Anki: Easy (4)", ankiAction = AnkiAction.FLIP_OR_EASY),
    ANKI_FLIP_ONLY("Anki: Flip Card Only", ankiAction = AnkiAction.FLIP_CARD),
    MW_STEER_LEFT("MyWhoosh: Steer Left", myWhooshAction = MyWhooshAction.STEER_LEFT),
    MW_STEER_RIGHT("MyWhoosh: Steer Right", myWhooshAction = MyWhooshAction.STEER_RIGHT),
    MW_CAMERA("MyWhoosh: Toggle Camera", myWhooshAction = MyWhooshAction.TOGGLE_CAMERA),
    MW_EMOTE("MyWhoosh: Emote", myWhooshAction = MyWhooshAction.EMOTE),
    MW_HUD("MyWhoosh: Toggle HUD", myWhooshAction = MyWhooshAction.HUD_TOGGLE),
    MW_CONFIRM("MyWhoosh: UI Select", myWhooshAction = MyWhooshAction.UI_CONFIRM),
    MW_BACK("MyWhoosh: UI Back", myWhooshAction = MyWhooshAction.UI_BACK),
    MW_POWERUP("MyWhoosh: Power-Up", myWhooshAction = MyWhooshAction.USE_POWERUP)
}

/**
 * Per-Mode binding key so each OperatingMode has its own independent button customization.
 */
data class ModeButtonBindingKey(
    val mode: OperatingMode,
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
    val customBindings: Map<ModeButtonBindingKey, MappedTargetAction> = emptyMap()
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

### 4.2 `app/src/main/java/com/ankiride/bridge/core/ButtonMappingStore.kt`

Provides:
1. Default per-mode button mappings implementing your **Right-Pod 4-Colorful-Button Split**:
   - **`Z` (Top Orange)** = `MW_SHIFT_UP`
   - **`A` (Bottom Blue)** = `MW_SHIFT_DOWN`
   - **`Y` (Left Yellow)** = `ANKI_FLIP_OR_AGAIN` ("Didn't Know") + Long-Press `ANKI_UNDO`
   - **`B` (Right Green)** = `ANKI_FLIP_OR_GOOD` ("Good") + Long-Press `ANKI_UNDO`
2. Persistent `SharedPreferences` load/save for per-mode overrides, plus one-tap presets (`applyPresetVerticalShiftHorizontalAnki` and `applyPresetTopRightShiftBottomLeftAnki`).

```kotlin
package com.ankiride.bridge.core

import android.content.Context

object ButtonMappingStore {
    private const val PREFS_NAME = "ankiride_per_mode_bindings"

    /**
     * Resolves the active MappedTargetAction for a given (mode, button, pressType),
     * checking per-mode user overrides first, then falling back to the mode's default preset.
     */
    fun resolveAction(
        mode: OperatingMode,
        button: ClickButton,
        pressType: PressType,
        customBindings: Map<ModeButtonBindingKey, MappedTargetAction>
    ): MappedTargetAction {
        val canonical = button.canonicalButton
        val key = ModeButtonBindingKey(mode, canonical, pressType)
        customBindings[key]?.let { return it }
        return defaultActionFor(mode, canonical, pressType)
    }

    fun defaultActionFor(
        mode: OperatingMode,
        button: ClickButton,
        pressType: PressType
    ): MappedTargetAction {
        val canonical = button.canonicalButton
        return when (mode) {
            OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH -> {
                // Default 4-Colorful-Button Split:
                // Z (Top Orange) = Shift Up (+), A (Bottom Blue) = Shift Down (–)
                // Y (Left Yellow) = Anki Didn't Know (Again), B (Right Green) = Anki Good
                when (canonical) {
                    ClickButton.Z -> MappedTargetAction.MW_SHIFT_UP
                    ClickButton.A -> MappedTargetAction.MW_SHIFT_DOWN
                    ClickButton.Y -> if (pressType == PressType.LONG_PRESS) {
                        MappedTargetAction.ANKI_UNDO
                    } else {
                        MappedTargetAction.ANKI_FLIP_OR_AGAIN
                    }
                    ClickButton.B -> if (pressType == PressType.LONG_PRESS) {
                        MappedTargetAction.ANKI_UNDO
                    } else {
                        MappedTargetAction.ANKI_FLIP_OR_GOOD
                    }
                    ClickButton.SHIFT_UP_RIGHT -> if (pressType == PressType.LONG_PRESS) {
                        MappedTargetAction.MW_SHIFT_DOWN
                    } else {
                        MappedTargetAction.MW_SHIFT_UP
                    }
                    ClickButton.SHIFT_DOWN_LEFT -> MappedTargetAction.MW_SHIFT_DOWN
                    ClickButton.LEFT -> MappedTargetAction.MW_STEER_LEFT
                    ClickButton.RIGHT -> MappedTargetAction.MW_STEER_RIGHT
                    ClickButton.UP -> MappedTargetAction.MW_CAMERA
                    ClickButton.DOWN -> MappedTargetAction.MW_EMOTE
                    else -> MappedTargetAction.NONE
                }
            }

            OperatingMode.MODE_3_ANKI_ONLY -> {
                when (canonical) {
                    ClickButton.Y -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_OR_AGAIN
                    ClickButton.B -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_OR_GOOD
                    ClickButton.A -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_FLIP_OR_EASY else MappedTargetAction.ANKI_FLIP_OR_GOOD
                    ClickButton.Z -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_OR_HARD
                    ClickButton.SHIFT_UP_RIGHT -> MappedTargetAction.ANKI_FLIP_OR_GOOD
                    ClickButton.SHIFT_DOWN_LEFT, ClickButton.LEFT -> MappedTargetAction.ANKI_UNDO
                    ClickButton.RIGHT, ClickButton.UP, ClickButton.DOWN -> MappedTargetAction.ANKI_FLIP_ONLY
                    else -> MappedTargetAction.NONE
                }
            }

            OperatingMode.MODE_4_MYWHOOSH_ONLY -> {
                when (canonical) {
                    ClickButton.Z, ClickButton.SHIFT_UP_RIGHT -> MappedTargetAction.MW_SHIFT_UP
                    ClickButton.A, ClickButton.SHIFT_DOWN_LEFT -> MappedTargetAction.MW_SHIFT_DOWN
                    ClickButton.Y, ClickButton.LEFT -> MappedTargetAction.MW_STEER_LEFT
                    ClickButton.B, ClickButton.RIGHT -> MappedTargetAction.MW_STEER_RIGHT
                    ClickButton.UP -> MappedTargetAction.MW_CAMERA
                    ClickButton.DOWN -> MappedTargetAction.MW_EMOTE
                    else -> MappedTargetAction.NONE
                }
            }
        }
    }

    fun loadBindings(context: Context): Map<ModeButtonBindingKey, MappedTargetAction> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val result = mutableMapOf<ModeButtonBindingKey, MappedTargetAction>()
        for ((rawKey, rawVal) in prefs.all) {
            val parts = rawKey.split("|")
            if (parts.size != 3) continue
            val mode = OperatingMode.entries.firstOrNull { it.name == parts[0] } ?: continue
            val button = ClickButton.entries.firstOrNull { it.name == parts[1] } ?: continue
            val press = PressType.entries.firstOrNull { it.name == parts[2] } ?: continue
            val target = MappedTargetAction.entries.firstOrNull { it.name == (rawVal as? String) } ?: continue
            result[ModeButtonBindingKey(mode, button, press)] = target
        }
        return result
    }

    fun saveBindings(context: Context, bindings: Map<ModeButtonBindingKey, MappedTargetAction>) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.clear()
        for ((k, v) in bindings) {
            editor.putString("${k.mode.name}|${k.button.name}|${k.pressType.name}", v.name)
        }
        editor.apply()
    }
}
```

---

## 5. Phase 1C (`SA-3-MYWHOOSH`): Zero-Latency Split-Screen Touch Dispatcher

### 5.1 `app/src/main/java/com/ankiride/bridge/mywhoosh/MyWhooshNetworkDispatcherImpl.kt`

Executes the local `MyShiftGestureFallback` screen tap **first with zero delay** in `Mode 1 (Tablet Split-Screen)` and `Mode 4 (MyWhoosh-Only)` before/alongside the network socket write so split-screen shifting never waits on TCP hold timers:

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
        val isTabletMyWhoosh = config.mode == OperatingMode.MODE_1_TABLET_SPLIT_SCREEN ||
            config.mode == OperatingMode.MODE_4_MYWHOOSH_ONLY

        // 1. Fire immediate 0ms-latency on-screen MyShift touch tap first in Tablet Modes
        var deliveredTouch = false
        if (isTabletMyWhoosh && config.useMyShiftTouchFallbackWhenNoClient &&
            (action == MyWhooshAction.SHIFT_UP || action == MyWhooshAction.SHIFT_DOWN)
        ) {
            val service = accessibilityServiceSupplier()
            if (service != null) {
                deliveredTouch = MyShiftGestureFallback.performTouchAction(service, action, config)
            } else {
                DiagnosticLogger.warn(LogCategory.MYWHOOSH, "AccessibilityService not active for MyShift touch tap")
            }
        }

        // 2. Also send official binary OpenBikeControl packet (0x01) if a client is connected
        var deliveredNetwork = false
        if (client.hasConnectedClients()) {
            deliveredNetwork = client.sendAction("127.0.0.1", config.openBikeServerPort, action)
        }

        return deliveredTouch || deliveredNetwork
    }
}
```

---

## 6. Phase 2 (`SA-4-APP-UI`): Per-Mode `ActionRouter.kt` & Color-Coded Button Customizer UI in `MainActivity.kt`

### 6.1 `app/src/main/java/com/ankiride/bridge/service/ActionRouter.kt`

Routes every button press through `ButtonMappingStore.resolveAction(config.mode, event.button, event.type, config.customBindings)` so every mode's default and custom bindings work seamlessly:

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
        val target = ButtonMappingStore.resolveAction(
            mode = config.mode,
            button = event.button,
            pressType = event.type,
            customBindings = config.customBindings
        )

        DiagnosticLogger.info(
            LogCategory.ROUTER,
            "[${config.mode.shortTitle}] ${event.button.displayName} (${event.type.name}) -> ${target.label}"
        )

        target.ankiAction?.let { ankiAction ->
            ankiDispatcher.dispatch(ankiAction)
        }
        target.myWhooshAction?.let { mwAction ->
            scope.launch {
                myWhooshDispatcher.dispatch(mwAction, config)
            }
        }
    }
}
```

### 6.2 `CustomButtonMappingCard` in `app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt`

Replaces `CustomButtonMappingCard` and `ButtonBindingRow` in `MainActivity.kt` with a **Per-Mode Color-Coded Visual Controller Editor** featuring:
- **Mode Selector Tabs** (`Split-Screen`, `Tablet + Phone`, `Anki-Only`, `MyWhoosh-Only`) so you can inspect and customize each mode independently.
- **One-Tap Right-Pod Layout Presets** for the 4 colorful buttons:
  1. **`Preset 1: Z/A = Shift Up/Down • Y/B = Didn't Know / Good`** (Default)
  2. **`Preset 2: Z/B = Shift Up/Down • Y/A = Didn't Know / Good`**
- **Color-Coded Button Badges** (`Z` Orange, `A` Blue, `Y` Yellow, `B` Green, `+` Rocker) showing both the **Single Press** and **Long Press (450ms)** actions side-by-side on each button card.

```kotlin
@Composable
fun CustomButtonMappingCard(
    activeMode: OperatingMode,
    rightPodOnly: Boolean,
    customBindings: Map<ModeButtonBindingKey, MappedTargetAction>,
    onUpdateBindings: (Map<ModeButtonBindingKey, MappedTargetAction>) -> Unit
) {
    var editingMode by remember(activeMode) { mutableStateOf(activeMode) }
    val buttonsToConfigure = remember(rightPodOnly) {
        val rightButtons = listOf(
            ClickButton.Z,
            ClickButton.A,
            ClickButton.Y,
            ClickButton.B,
            ClickButton.SHIFT_UP_RIGHT
        )
        val leftButtons = listOf(
            ClickButton.SHIFT_DOWN_LEFT,
            ClickButton.LEFT,
            ClickButton.RIGHT,
            ClickButton.UP,
            ClickButton.DOWN
        )
        if (rightPodOnly) rightButtons else rightButtons + leftButtons
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Button Mapping by Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Each mode saves its own independent button layout",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        val cleared = customBindings.filterKeys { it.mode != editingMode }
                        onUpdateBindings(cleared)
                        DiagnosticLogger.info(LogCategory.ROUTER, "Reset ${editingMode.shortTitle} bindings to default")
                    }
                ) {
                    Text("Reset Mode", fontSize = 11.sp)
                }
            }

            // 1. Mode Tab Bar to select which mode's buttons to customize
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OperatingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = editingMode == mode,
                        onClick = { editingMode = mode },
                        label = {
                            Text(
                                text = if (mode == activeMode) "★ ${mode.shortTitle}" else mode.shortTitle,
                                fontSize = 12.sp,
                                fontWeight = if (editingMode == mode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 2. Quick 1-Tap Presets for Combined Modes (Right Pod 4 Colorful Buttons)
            if (editingMode == OperatingMode.MODE_1_TABLET_SPLIT_SCREEN ||
                editingMode == OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val updated = customBindings.toMutableMap()
                            updated[ModeButtonBindingKey(editingMode, ClickButton.Z, PressType.SINGLE_CLICK)] = MappedTargetAction.MW_SHIFT_UP
                            updated[ModeButtonBindingKey(editingMode, ClickButton.A, PressType.SINGLE_CLICK)] = MappedTargetAction.MW_SHIFT_DOWN
                            updated[ModeButtonBindingKey(editingMode, ClickButton.Y, PressType.SINGLE_CLICK)] = MappedTargetAction.ANKI_FLIP_OR_AGAIN
                            updated[ModeButtonBindingKey(editingMode, ClickButton.B, PressType.SINGLE_CLICK)] = MappedTargetAction.ANKI_FLIP_OR_GOOD
                            onUpdateBindings(updated)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Preset: Z/A Shift • Y/B Anki", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val updated = customBindings.toMutableMap()
                            updated[ModeButtonBindingKey(editingMode, ClickButton.Z, PressType.SINGLE_CLICK)] = MappedTargetAction.MW_SHIFT_UP
                            updated[ModeButtonBindingKey(editingMode, ClickButton.B, PressType.SINGLE_CLICK)] = MappedTargetAction.MW_SHIFT_DOWN
                            updated[ModeButtonBindingKey(editingMode, ClickButton.Y, PressType.SINGLE_CLICK)] = MappedTargetAction.ANKI_FLIP_OR_AGAIN
                            updated[ModeButtonBindingKey(editingMode, ClickButton.A, PressType.SINGLE_CLICK)] = MappedTargetAction.ANKI_FLIP_OR_GOOD
                            onUpdateBindings(updated)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Preset: Z/B Shift • Y/A Anki", fontSize = 11.sp)
                    }
                }
            }

            // 3. Color-Coded Button Cards showing Single Press & Long Press for editingMode
            buttonsToConfigure.forEach { button ->
                ColorCodedButtonCard(
                    mode = editingMode,
                    button = button,
                    customBindings = customBindings,
                    onSelectAction = { pressType, target ->
                        val key = ModeButtonBindingKey(editingMode, button, pressType)
                        val updated = if (target == null) {
                            customBindings - key
                        } else {
                            customBindings + (key to target)
                        }
                        onUpdateBindings(updated)
                    }
                )
            }
        }
    }
}

@Composable
fun ColorCodedButtonCard(
    mode: OperatingMode,
    button: ClickButton,
    customBindings: Map<ModeButtonBindingKey, MappedTargetAction>,
    onSelectAction: (PressType, MappedTargetAction?) -> Unit
) {
    val singleResolved = ButtonMappingStore.resolveAction(mode, button, PressType.SINGLE_CLICK, customBindings)
    val longResolved = ButtonMappingStore.resolveAction(mode, button, PressType.LONG_PRESS, customBindings)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(button.badgeColorHex), shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = button.shortBadge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = button.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            GestureActionPickerRow(
                gestureLabel = "Single Press",
                resolvedAction = singleResolved,
                isCustom = customBindings.containsKey(ModeButtonBindingKey(mode, button, PressType.SINGLE_CLICK)),
                onSelect = { onSelectAction(PressType.SINGLE_CLICK, it) }
            )

            GestureActionPickerRow(
                gestureLabel = "Long Press (450ms)",
                resolvedAction = longResolved,
                isCustom = customBindings.containsKey(ModeButtonBindingKey(mode, button, PressType.LONG_PRESS)),
                onSelect = { onSelectAction(PressType.LONG_PRESS, it) }
            )
        }
    }
}

@Composable
fun GestureActionPickerRow(
    gestureLabel: String,
    resolvedAction: MappedTargetAction,
    isCustom: Boolean,
    onSelect: (MappedTargetAction?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gestureLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = resolvedAction.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    resolvedAction.myWhooshAction != null -> Color(0xFF64B5F6)
                    resolvedAction.ankiAction != null -> Color(0xFF81C784)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Box {
            OutlinedButton(onClick = { menuExpanded = true }) {
                Text(if (isCustom) "Custom ▾" else "Change ▾", fontSize = 11.sp)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Reset to Mode Default") },
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
```

*(In `AnkiRideDashboard` inside `MainActivity.kt`, initialize `customBindings` from `ButtonMappingStore.loadBindings(context)` and call `ButtonMappingStore.saveBindings(context, updated)` inside `onUpdateBindings` so all per-mode customizations persist across app restarts.)*

---

## 7. Review & Security Gates (`RG-0` through `RG-5`)

| Gate ID | Target Phase / Subagent | Review & Security Verification Criteria | Automated Check Command |
| :--- | :--- | :--- | :--- |
| **`RG-0`** | Phase 0 (`ORCHESTRATOR`) | `Contracts.kt` defines `ModeButtonBindingKey(mode, button, pressType)` and `Z(0x0080, ...)`; `ButtonMappingStore.kt` sets `Z = MW_SHIFT_UP`, `A = MW_SHIFT_DOWN`, `Y = ANKI_FLIP_OR_AGAIN`, `B = ANKI_FLIP_OR_GOOD` as default for Modes 1 & 2 and persists per-mode bindings to `SharedPreferences`. | `grep -q "ModeButtonBindingKey" app/src/main/java/com/ankiride/bridge/core/Contracts.kt && test -f app/src/main/java/com/ankiride/bridge/core/ButtonMappingStore.kt` |
| **`RG-1`** | Phase 1A (`SA-1-BLE`) | `ZwiftPacketParser` parses `0x23` + `0x37`; `ZwiftGestureEngine` enforces `DEBOUNCE_WINDOW_MS = 120L`; `ZwiftBleController` merges `podActiveMasks`. | `grep -q "DEBOUNCE_WINDOW_MS" app/src/main/java/com/ankiride/bridge/ble/ZwiftGestureEngine.kt` |
| **`RG-2`** | Phase 1B (`SA-2-ANKI`) | `AnkiAccessibilityBridge` enforces `ANKI_PACKAGE`, `!node.isPassword`, `FLIP_TO_GRADE_GUARD_MS = 350L`, and `dumpInteractiveWindowsToLog`. | `grep -q "isPassword" app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt` |
| **`RG-3`** | Phase 1C (`SA-3-MYWHOOSH`) | `MyWhooshNetworkDispatcherImpl` fires immediate `75ms` `MyShiftGestureFallback` touch tap first in Tablet Modes; `MyShiftOverlayCalibrator.kt` provides draggable `TYPE_ACCESSIBILITY_OVERLAY` targets. | `grep -q "TYPE_ACCESSIBILITY_OVERLAY" app/src/main/java/com/ankiride/bridge/mywhoosh/MyShiftOverlayCalibrator.kt` |
| **`RG-4`** | Phase 2 (`SA-4-APP-UI`) | `ActionRouter` routes via `ButtonMappingStore.resolveAction`; `MainActivity.kt` renders `ColorCodedButtonCard` with per-mode tabs, 1-tap presets, and persistent `ButtonMappingStore.saveBindings`. | `grep -q "ColorCodedButtonCard" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt` |
| **`RG-5`** | Phase 3 (`SA-5-REVIEWER`) | All unit tests in `app/src/test/` updated for `ButtonMappingStore` and pass `./gradlew testDebugUnitTest --no-daemon`. | `./verify.sh && ./gradlew testDebugUnitTest --no-daemon` |

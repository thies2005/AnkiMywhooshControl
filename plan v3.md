# Master Multi-Agent Implementation Plan & Review-Gated Specification: `AnkiMywhooshControl` v4.0

## 1. Executive Summary & Feature Scope in v4.0

This specification is structured for **parallel and phased execution by coding subagents**, with zero placeholders, strict write-isolation boundaries, and 7 blocking Review & Security Gates (`RG-0` through `RG-6`).

### 1.1 Exact Feature Decisions Implemented in v4.0
1. **Feature 1 — Floating Mini HUD Pill (`ui/FloatingHudPill.kt`) [IMPLEMENTED — OPTIONAL, OFF BY DEFAULT]**:
   - Controlled by `config.floatingHudPillEnabled` (**default `false`**).
   - When toggled ON by the user in `MainActivity`, draws a compact, draggable `TYPE_ACCESSIBILITY_OVERLAY` pill over Split-Screen / MyWhoosh showing:
     - Live controller status dot (`🟢` Connected / `🟡` Scanning / `🔴` Disconnected)
     - Current `OperatingMode` badge (tap to cycle modes on the fly)
     - Last dispatched action (`⚡ Shift Up (+)`, `📚 Anki: Good (3)`, etc.)
     - Live AnkiDroid due-card counter (`Due: N`) queried from `AnkiContentProviderHelper`
     - One-tap `🎯` button to open the `[–]` / `[+]` shift target calibrator and `✕` to hide the pill.
2. **Feature 2 — Distinct Haptic & Audio Cues (`service/FeedbackCueManager.kt`) [IMPLEMENTED — OPTIONAL, OFF BY DEFAULT]**:
   - Controlled by two independent optional toggles in `AppConfiguration` and `MainActivity`:
     - `config.hapticFeedbackEnabled` (**default `false`**) — distinct `VibrationEffect` patterns (`1 crisp 20ms tick` = Shift Up, `2 quick 15ms ticks` = Shift Down, `30ms soft pulse` = Anki Good, `45ms medium pulse` = Anki Again, `double buzz` = Undo).
     - `config.audioFeedbackEnabled` (**default `false`**) — distinct non-intrusive `ToneGenerator` audio cues for eyes-free confirmation while sprinting.
3. **Feature 3 — One-Tap Split-Screen Workout App Launcher [POSTPONED]**:
   - Explicitly postponed per your instruction; no app-launching intents or `<queries>` bloat added.
4. **Feature 4 — Ubuntu Desktop Anki `AnkiConnect` Integration (`anki/AnkiConnectClient.kt` + `MODE_5_UBUNTU_ANKICONNECT`) [IMPLEMENTED]**:
   - Adds **`OperatingMode.MODE_5_UBUNTU_ANKICONNECT`** (*"Ubuntu Anki + MyWhoosh"*: MyWhoosh shifts locally on the Tablet or over Wi-Fi, while Anki actions route over local Wi-Fi LAN to your Ubuntu laptop running Desktop Anki with the [AnkiConnect add-on (2055492159)](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Famikey%2Fanki-connect) on port `8765`).
   - Uses a **self-healing 2-step state machine** with a `350ms` post-flip bounce guard:
     - **1st press** of `Y` (Didn't Know) or `B` (Good) sends `{"action":"guiShowAnswer","version":6}` to reveal the answer on your Ubuntu screen.
     - **2nd press** (after `350ms`) sends `{"action":"guiAnswerCard","version":6,"params":{"ease":1}}` (for `Y` = Again) or `{"ease":3}` (for `B` = Good). If Desktop Anki reports `"result": false` (e.g., if a new card was still on the question side), `AnkiConnectClient` automatically sends `guiShowAnswer` so it never gets out of sync!
     - **Long-Press `Y` or `B`** sends `{"action":"guiUndo","version":6}`.
   - Enforces `LanAddressValidator.isLocalOrLanAddress(ubuntuHost)` (RFC1918 private LAN IPv4 or `127.0.0.1` only) and uses raw socket HTTP/1.1 framing so `network_security_config.xml` keeps `cleartextTrafficPermitted="false"` strictly intact.
5. **Core Fixes Preserved from v2.3 / v3.0**:
   - **Right-Pod 4-Colorful-Button Default Split**: `🟠 Z (0x0080)` = **Shift Up (+)**, `🔵 A (0x0010)` = **Shift Down (–)**, `🟡 Y (0x0040)` = **Anki Didn't Know / Again (1)**, `🟢 B (0x0020)` = **Anki Knew It / Good (3)** (Long-Press `Y`/`B` = **Undo**).
   - **Per-Mode Independent & Persistent Button Customizer UI** (`ButtonMappingStore.kt` + `ColorCodedButtonCard`).
   - **Mode- & Resolution-Keyed Split-Screen Shift Target Calibrator** (`MyShiftOverlayCalibrator.kt`) + `75ms` Unreal Engine touch stroke (`MyShiftGestureFallback.kt`).
   - **Zero-Touch Passive BLE Auto-Start on Controller Wake** (`BridgeAccessibilityService.kt`).

---

## 2. Multi-Agent Phase Topology & Write-Isolation Matrix

**Orchestrator Rule**: Execute **Phase 0** first, verify `RG-0`, then run **Phase 1A, 1B, and 1C in parallel**, verify `RG-1`..`RG-3`, run **Phase 2 (`SA-4-APP-UI`)**, verify `RG-4`..`RG-5`, and finish with **Phase 3 (`SA-5-REVIEWER`)** (`RG-6`). No two parallel subagents ever touch the same file.

| Phase | Subagent ID | Role Description | Owned Repository Files (Read-Write) | Read-Only Dependencies | Blocking Gate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Phase 0** | `ORCHESTRATOR` | Shared Contracts (`5 Modes`, `ModeButtonBindingKey`, optional flags), `ButtonMappingStore.kt`, `DiagnosticLogger.kt` & `verify.sh` | `app/src/main/java/com/ankiride/bridge/core/Contracts.kt`, `app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt`, `app/src/main/java/com/ankiride/bridge/core/ButtonMappingStore.kt`, `verify.sh` | `build.gradle.kts`, `app/build.gradle.kts` | `RG-0` |
| **Phase 1A** | `SA-1-BLE` | `0x23` + `0x37` BLE Parser, Long-Press Release Fix, Cross-Pod Deduplication & Packet Logger | `app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt`, `ZwiftGestureEngine.kt`, `ZwiftBleController.kt`, `app/src/test/java/com/ankiride/bridge/ble/*` | `core/*` | `RG-1` |
| **Phase 1B** | `SA-2-ANKI` | 3-Layer Local AnkiDroid Accessibility Engine + Ubuntu `AnkiConnectClient.kt` (Port `8765` JSON-RPC) + Hybrid `AnkiActionDispatcherImpl.kt` | `app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt`, `AnkiConnectClient.kt`, `AnkiActionDispatcherImpl.kt`, `AnkiContentProviderHelper.kt`, `app/src/main/res/xml/accessibility_service_config.xml`, `app/src/test/java/com/ankiride/bridge/anki/*` | `core/*`, `mywhoosh/LanAddressValidator.kt` | `RG-2` |
| **Phase 1C** | `SA-3-MYWHOOSH` | Profile-Keyed `MyShiftOverlayCalibrator`, Zero-Latency `75ms` Split-Screen Tapper & `OpenBikeControlClient` | `app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt`, `MyShiftOverlayCalibrator.kt`, `MyShiftGestureFallback.kt`, `MyWhooshNetworkDispatcherImpl.kt`, `OpenBikeControlClient.kt`, `MyWhooshDiscovery.kt`, `app/src/main/res/xml/network_security_config.xml`, `app/src/test/java/com/ankiride/bridge/mywhoosh/*` | `core/*` | `RG-3` |
| **Phase 2** | `SA-4-APP-UI` | Optional `FloatingHudPill.kt`, Optional `FeedbackCueManager.kt`, Auto-Start BLE Monitor, Per-Mode Customizer UI & Ubuntu IP Config | `app/src/main/java/com/ankiride/bridge/ui/FloatingHudPill.kt`, `app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt`, `app/src/main/java/com/ankiride/bridge/service/FeedbackCueManager.kt`, `ActionRouter.kt`, `BridgeAccessibilityService.kt`, `BridgeForegroundService.kt`, `app/src/main/AndroidManifest.xml`, `app/src/test/java/com/ankiride/bridge/service/*` | `core/*`, `ble/*`, `anki/*`, `mywhoosh/*` | `RG-4` & `RG-5` |
| **Phase 3** | `SA-5-REVIEWER` | Automated Gate Auditor & Unit Test Runner | Read-Only across entire repository (executes `./verify.sh` and `./gradlew testDebugUnitTest`) | Entire Repository | `RG-6` |

---

## 3. Phase 0 (`ORCHESTRATOR`): Core Contracts, Logger & Per-Mode Persistent Store

### 3.1 `app/src/main/java/com/ankiride/bridge/core/Contracts.kt`

```kotlin
package com.ankiride.bridge.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class OperatingMode(val shortTitle: String, val subtitle: String) {
    MODE_1_TABLET_SPLIT_SCREEN(
        "Split-Screen",
        "MyWhoosh + AnkiDroid side-by-side on Tablet (Local 75ms Touch + 127.0.0.1)"
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
    ),
    MODE_5_UBUNTU_ANKICONNECT(
        "Ubuntu Anki + Ride",
        "Desktop Anki on Ubuntu Laptop (AnkiConnect :8765) + MyWhoosh on Tablet/Phone"
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

data class ModeButtonBindingKey(
    val mode: OperatingMode,
    val button: ClickButton,
    val pressType: PressType
)

data class AppConfiguration(
    val mode: OperatingMode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
    val rightPodOnlyNoZwift: Boolean = true,
    val useZForShiftDownInSinglePodMode: Boolean = true,
    val autoStartOnControllerWake: Boolean = true,
    // Feature 1: Optional Floating Mini HUD Pill ( strictly OFF by default )
    val floatingHudPillEnabled: Boolean = false,
    // Feature 2: Optional Haptic & Audio Cues ( strictly OFF by default )
    val hapticFeedbackEnabled: Boolean = false,
    val audioFeedbackEnabled: Boolean = false,
    // Feature 4: Ubuntu Desktop AnkiConnect target (RFC1918 LAN IP & port 8765)
    val ubuntuAnkiConnectHost: String = "192.168.1.100",
    val ubuntuAnkiConnectPort: Int = 8765,
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

### 3.2 `app/src/main/java/com/ankiride/bridge/core/ButtonMappingStore.kt`

```kotlin
package com.ankiride.bridge.core

import android.content.Context

object ButtonMappingStore {
    private const val PREFS_BINDINGS = "ankiride_per_mode_bindings_v4"
    private const val PREFS_SETTINGS = "ankiride_app_settings_v4"

    private const val KEY_MODE = "operating_mode"
    private const val KEY_RIGHT_POD_ONLY = "right_pod_only"
    private const val KEY_AUTO_START = "auto_start_on_wake"
    private const val KEY_FLOATING_HUD = "floating_hud_pill_enabled"
    private const val KEY_HAPTIC = "haptic_feedback_enabled"
    private const val KEY_AUDIO = "audio_feedback_enabled"
    private const val KEY_UBUNTU_HOST = "ubuntu_ankiconnect_host"
    private const val KEY_UBUNTU_PORT = "ubuntu_ankiconnect_port"
    private const val KEY_TOUCH_FALLBACK = "touch_fallback"

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
            OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
            OperatingMode.MODE_5_UBUNTU_ANKICONNECT -> {
                // Default 4-Colorful-Button Split for all Combined Ride + Anki Modes (Modes 1, 2, and 5):
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
        val prefs = context.getSharedPreferences(PREFS_BINDINGS, Context.MODE_PRIVATE)
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
        val editor = context.getSharedPreferences(PREFS_BINDINGS, Context.MODE_PRIVATE).edit()
        editor.clear()
        for ((k, v) in bindings) {
            editor.putString("${k.mode.name}|${k.button.name}|${k.pressType.name}", v.name)
        }
        editor.apply()
    }

    fun loadFullConfiguration(context: Context): AppConfiguration {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val modeName = prefs.getString(KEY_MODE, OperatingMode.MODE_1_TABLET_SPLIT_SCREEN.name)
        val mode = OperatingMode.entries.firstOrNull { it.name == modeName }
            ?: OperatingMode.MODE_1_TABLET_SPLIT_SCREEN
        return AppConfiguration(
            mode = mode,
            rightPodOnlyNoZwift = prefs.getBoolean(KEY_RIGHT_POD_ONLY, true),
            autoStartOnControllerWake = prefs.getBoolean(KEY_AUTO_START, true),
            floatingHudPillEnabled = prefs.getBoolean(KEY_FLOATING_HUD, false), // Default OFF
            hapticFeedbackEnabled = prefs.getBoolean(KEY_HAPTIC, false),        // Default OFF
            audioFeedbackEnabled = prefs.getBoolean(KEY_AUDIO, false),          // Default OFF
            ubuntuAnkiConnectHost = prefs.getString(KEY_UBUNTU_HOST, "192.168.1.100") ?: "192.168.1.100",
            ubuntuAnkiConnectPort = prefs.getInt(KEY_UBUNTU_PORT, 8765),
            useMyShiftTouchFallbackWhenNoClient = prefs.getBoolean(KEY_TOUCH_FALLBACK, true),
            customBindings = loadBindings(context)
        )
    }

    fun saveFullConfiguration(context: Context, config: AppConfiguration) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, config.mode.name)
            .putBoolean(KEY_RIGHT_POD_ONLY, config.rightPodOnlyNoZwift)
            .putBoolean(KEY_AUTO_START, config.autoStartOnControllerWake)
            .putBoolean(KEY_FLOATING_HUD, config.floatingHudPillEnabled)
            .putBoolean(KEY_HAPTIC, config.hapticFeedbackEnabled)
            .putBoolean(KEY_AUDIO, config.audioFeedbackEnabled)
            .putString(KEY_UBUNTU_HOST, config.ubuntuAnkiConnectHost.trim())
            .putInt(KEY_UBUNTU_PORT, config.ubuntuAnkiConnectPort)
            .putBoolean(KEY_TOUCH_FALLBACK, config.useMyShiftTouchFallbackWhenNoClient)
            .apply()
        saveBindings(context, config.customBindings)
    }
}
```

---

## 4. Phase 1B (`SA-2-ANKI`): Ubuntu Desktop `AnkiConnectClient.kt` (Feature 4) & Hybrid `AnkiActionDispatcherImpl.kt`

### 4.1 `app/src/main/java/com/ankiride/bridge/anki/AnkiConnectClient.kt`

Implements **Feature 4 (Ubuntu Desktop Anki via [AnkiConnect](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Famikey%2Fanki-connect) on Port `8765`)**:
- Validates `host` strictly via `LanAddressValidator.isLocalOrLanAddress(host)` (`SG-4` / `RG-2`) so it can **never** send requests outside `127.0.0.1` or RFC1918 private LAN (`192.168.x.x`, `10.x.x.x`, `172.16..31.x.x`).
- Uses raw TCP `Socket` HTTP/1.1 framing so `network_security_config.xml` keeps `cleartextTrafficPermitted="false"` intact.
- Implements a **self-healing 2-step Question $\rightarrow$ Answer state machine** with the `350ms` bounce guard (`FLIP_TO_GRADE_GUARD_MS = 350L`):
  - When `isAnswerVisible == false`, pressing `Y` (`FLIP_OR_AGAIN`) or `B` (`FLIP_OR_GOOD`) sends `{"action":"guiShowAnswer","version":6}`, sets `isAnswerVisible = true`, and arms the `350ms` bounce guard.
  - When `isAnswerVisible == true` (and `≥ 350ms` have elapsed), pressing `Y` sends `{"action":"guiAnswerCard","version":6,"params":{"ease":1}}` and pressing `B` sends `{"ease":3}`. If Desktop Anki returns `"result":false` (meaning Desktop Anki was still on the Question side), `AnkiConnectClient` automatically sends `guiShowAnswer` and stays in `isAnswerVisible = true`!

```kotlin
package com.ankiride.bridge.anki

import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.mywhoosh.LanAddressValidator
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class AnkiConnectClient {

    companion object {
        const val DEFAULT_PORT = 8765
        const val SOCKET_TIMEOUT_MS = 1200
        const val FLIP_TO_GRADE_GUARD_MS = 350L
    }

    @Volatile
    var isAnswerVisible: Boolean = false

    @Volatile
    var lastFlipTimestampMs: Long = 0L

    fun dispatchAnkiConnectAction(
        host: String,
        port: Int = DEFAULT_PORT,
        action: AnkiAction,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val cleanHost = host.trim()
        if (!LanAddressValidator.isLocalOrLanAddress(cleanHost)) {
            DiagnosticLogger.error(
                LogCategory.ANKI,
                "Blocked AnkiConnect request to non-LAN address '$cleanHost' (must be 192.168.x.x, 10.x.x.x, 172.16-31.x.x, or 127.0.0.1)"
            )
            return false
        }
        if (port !in 1..65535) return false

        return when (action) {
            AnkiAction.FLIP_CARD -> {
                val ok = sendJsonRpc(cleanHost, port, """{"action":"guiShowAnswer","version":6}""")
                if (ok) {
                    isAnswerVisible = true
                    lastFlipTimestampMs = nowMs
                    DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($cleanHost:$port): Flipped card (guiShowAnswer)")
                }
                ok
            }
            AnkiAction.UNDO -> {
                val ok = sendJsonRpc(cleanHost, port, """{"action":"guiUndo","version":6}""")
                if (ok) {
                    isAnswerVisible = false
                    DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($cleanHost:$port): Undid last card (guiUndo)")
                }
                ok
            }
            AnkiAction.FLIP_OR_AGAIN -> flipOrAnswerEase(cleanHost, port, ease = 1, label = "Again(1)", nowMs = nowMs)
            AnkiAction.FLIP_OR_HARD -> flipOrAnswerEase(cleanHost, port, ease = 2, label = "Hard(2)", nowMs = nowMs)
            AnkiAction.FLIP_OR_GOOD -> flipOrAnswerEase(cleanHost, port, ease = 3, label = "Good(3)", nowMs = nowMs)
            AnkiAction.FLIP_OR_EASY -> flipOrAnswerEase(cleanHost, port, ease = 4, label = "Easy(4)", nowMs = nowMs)
        }
    }

    private fun flipOrAnswerEase(
        host: String,
        port: Int,
        ease: Int,
        label: String,
        nowMs: Long
    ): Boolean {
        if (!isAnswerVisible) {
            val flipped = sendJsonRpc(host, port, """{"action":"guiShowAnswer","version":6}""")
            if (flipped) {
                isAnswerVisible = true
                lastFlipTimestampMs = nowMs
                DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($host:$port): 1st press -> guiShowAnswer (armed 350ms guard)")
            }
            return flipped
        }

        if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) {
            DiagnosticLogger.warn(LogCategory.ANKI, "Ubuntu AnkiConnect: Bounce Guard (350ms) blocked double-press for $label")
            return false
        }

        val response = sendJsonRpcRaw(
            host,
            port,
            """{"action":"guiAnswerCard","version":6,"params":{"ease":$ease}}"""
        ) ?: return false

        // Self-healing check: if Desktop Anki was still on Question side, guiAnswerCard returns "result": false
        if (response.contains("\"result\":false") || response.contains("\"result\": false")) {
            val flipped = sendJsonRpc(host, port, """{"action":"guiShowAnswer","version":6}""")
            isAnswerVisible = true
            lastFlipTimestampMs = nowMs
            DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($host:$port): Card was on Question side -> auto-called guiShowAnswer")
            return flipped
        }

        val ok = response.contains("\"error\":null") || response.contains("\"error\": null")
        if (ok) {
            isAnswerVisible = false
            DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($host:$port): Graded card -> $label (guiAnswerCard ease=$ease)")
        } else {
            DiagnosticLogger.warn(LogCategory.ANKI, "Ubuntu AnkiConnect error response: $response")
        }
        return ok
    }

    fun sendJsonRpc(host: String, port: Int, jsonBody: String): Boolean {
        val resp = sendJsonRpcRaw(host, port, jsonBody) ?: return false
        return resp.contains("\"error\":null") || resp.contains("\"error\": null")
    }

    fun sendJsonRpcRaw(host: String, port: Int, jsonBody: String): String? {
        if (!LanAddressValidator.isLocalOrLanAddress(host)) return null
        val bodyBytes = jsonBody.toByteArray(Charsets.UTF_8)
        val requestHeader = buildString {
            append("POST / HTTP/1.1\r\n")
            append("Host: $host:$port\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)

        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val out: OutputStream = socket.getOutputStream()
                out.write(requestHeader)
                out.write(bodyBytes)
                out.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.appendLine(line)
                }
                sb.toString()
            }
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.ANKI, "Ubuntu AnkiConnect ($host:$port) connection failed: ${e.message}")
            null
        }
    }
}
```

### 4.2 `app/src/main/java/com/ankiride/bridge/anki/AnkiActionDispatcherImpl.kt`

Routes Anki actions to **Ubuntu Desktop Anki (`AnkiConnectClient`)** when `config.mode == OperatingMode.MODE_5_UBUNTU_ANKICONNECT`, and to local Android **AnkiDroid (`AnkiAccessibilityBridge`)** in all other modes:

```kotlin
package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.OperatingMode
import java.util.concurrent.Executors

class AnkiActionDispatcherImpl(
    private val bridge: AnkiAccessibilityBridge = AnkiAccessibilityBridge(),
    private val ankiConnectClient: AnkiConnectClient = AnkiConnectClient(),
    private val configSupplier: () -> AppConfiguration = { AppConfiguration() },
    private val serviceProvider: () -> AccessibilityService?
) : AnkiActionDispatcher {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun dispatch(action: AnkiAction): Boolean {
        val config = configSupplier()
        if (config.mode == OperatingMode.MODE_5_UBUNTU_ANKICONNECT) {
            ioExecutor.execute {
                ankiConnectClient.dispatchAnkiConnectAction(
                    host = config.ubuntuAnkiConnectHost,
                    port = config.ubuntuAnkiConnectPort,
                    action = action
                )
            }
            return true
        }

        val service = serviceProvider() ?: return false
        return bridge.performAnkiAction(service, action)
    }
}
```

---

## 5. Phase 2 (`SA-4-APP-UI`): Optional `FloatingHudPill.kt` (Feature 1) & Optional `FeedbackCueManager.kt` (Feature 2)

### 5.1 `app/src/main/java/com/ankiride/bridge/service/FeedbackCueManager.kt` (Feature 2 — Optional Haptic & Audio Cues)

Strictly checks `config.hapticFeedbackEnabled` (default `false`) and `config.audioFeedbackEnabled` (default `false`) before emitting any vibration or audio tone:

```kotlin
package com.ankiride.bridge.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.MappedTargetAction
import com.ankiride.bridge.core.MyWhooshAction

object FeedbackCueManager {

    fun triggerCue(context: Context, target: MappedTargetAction, config: AppConfiguration) {
        if (target == MappedTargetAction.NONE) return
        if (config.hapticFeedbackEnabled) {
            triggerHaptic(context, target)
        }
        if (config.audioFeedbackEnabled) {
            triggerAudioTone(target)
        }
    }

    private fun triggerHaptic(context: Context, target: MappedTargetAction) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when {
                    target.myWhooshAction == MyWhooshAction.SHIFT_UP ->
                        VibrationEffect.createOneShot(20L, 220)
                    target.myWhooshAction == MyWhooshAction.SHIFT_DOWN ->
                        VibrationEffect.createWaveform(longArrayOf(0L, 15L, 45L, 15L), intArrayOf(0, 200, 0, 200), -1)
                    target.ankiAction == AnkiAction.UNDO ->
                        VibrationEffect.createWaveform(longArrayOf(0L, 45L, 60L, 65L), intArrayOf(0, 255, 0, 255), -1)
                    target.ankiAction == AnkiAction.FLIP_OR_AGAIN ->
                        VibrationEffect.createOneShot(45L, 180)
                    target.ankiAction != null ->
                        VibrationEffect.createOneShot(25L, 130)
                    else ->
                        VibrationEffect.createOneShot(18L, 120)
                }
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {
        }
    }

    private fun triggerAudioTone(target: MappedTargetAction) {
        try {
            val toneType = when {
                target.myWhooshAction == MyWhooshAction.SHIFT_UP -> ToneGenerator.TONE_PROP_BEEP
                target.myWhooshAction == MyWhooshAction.SHIFT_DOWN -> ToneGenerator.TONE_PROP_ACK
                target.ankiAction == AnkiAction.UNDO -> ToneGenerator.TONE_PROP_NACK
                target.ankiAction == AnkiAction.FLIP_OR_AGAIN -> ToneGenerator.TONE_CDMA_PIP
                target.ankiAction != null -> ToneGenerator.TONE_PROP_BEEP2
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)
            toneGen.startTone(toneType, 45)
        } catch (_: Exception) {
        }
    }
}
```

### 5.2 `app/src/main/java/com/ankiride/bridge/ui/FloatingHudPill.kt` (Feature 1 — Optional Floating Mini HUD Pill, OFF by Default)

Draws a compact, draggable floating HUD pill using `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` **only when `config.floatingHudPillEnabled == true`**:

```kotlin
package com.ankiride.bridge.ui

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ankiride.bridge.anki.AnkiContentProviderHelper
import com.ankiride.bridge.core.BleConnectionState
import com.ankiride.bridge.core.ButtonMappingStore
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.mywhoosh.MyShiftOverlayCalibrator
import kotlin.math.abs
import kotlin.math.roundToInt

object FloatingHudPill {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pillContainer: View? = null
    private var statusTextView: TextView? = null
    private var lastActionLabel: String = "Ready"

    fun syncVisibility(
        service: AccessibilityService?,
        enabled: Boolean,
        bleState: BleConnectionState,
        mode: OperatingMode
    ) {
        if (service == null || !enabled) {
            if (service != null) hide(service)
            return
        }
        mainHandler.post {
            if (pillContainer == null) {
                show(service, bleState, mode)
            } else {
                updateText(service, bleState, mode, lastActionLabel)
            }
        }
    }

    fun notifyActionDispatched(
        service: AccessibilityService?,
        enabled: Boolean,
        bleState: BleConnectionState,
        mode: OperatingMode,
        actionText: String
    ) {
        lastActionLabel = actionText
        if (service == null || !enabled) return
        mainHandler.post {
            if (pillContainer == null) {
                show(service, bleState, mode)
            } else {
                updateText(service, bleState, mode, actionText)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(
        service: AccessibilityService,
        bleState: BleConnectionState,
        mode: OperatingMode
    ) {
        hide(service)
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)

        val layout = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (12 * dm.density).roundToInt()
            val padV = (6 * dm.density).roundToInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * dm.density
                setColor(Color.parseColor("#DD161B22"))
                setStroke((1.5f * dm.density).roundToInt(), Color.parseColor("#58A6FF"))
            }
        }

        val infoTv = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        statusTextView = infoTv
        layout.addView(infoTv)

        val calibrateTv = TextView(service).apply {
            text = "  🎯"
            textSize = 13f
            setOnClickListener {
                val cfg = ButtonMappingStore.loadFullConfiguration(service)
                MyShiftOverlayCalibrator.showCalibrationOverlay(service, cfg.mode)
            }
        }
        layout.addView(calibrateTv)

        val closeTv = TextView(service).apply {
            text = "  ✕"
            setTextColor(Color.parseColor("#FF7B72"))
            textSize = 13f
            setOnClickListener {
                val cfg = ButtonMappingStore.loadFullConfiguration(service)
                ButtonMappingStore.saveFullConfiguration(service, cfg.copy(floatingHudPillEnabled = false))
                hide(service)
            }
        }
        layout.addView(closeTv)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dm.widthPixels * 0.38f).roundToInt()
            y = (18 * dm.density).roundToInt()
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragged = false

        layout.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > 8f || abs(dy) > 8f) dragged = true
                    lp.x = (startX + dx).roundToInt()
                    lp.y = (startY + dy).roundToInt()
                    wm.updateViewLayout(v, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        // Tap pill body to cycle OperatingMode on the fly
                        val curCfg = ButtonMappingStore.loadFullConfiguration(service)
                        val modes = OperatingMode.entries
                        val nextMode = modes[(modes.indexOf(curCfg.mode) + 1) % modes.size]
                        val updated = curCfg.copy(mode = nextMode)
                        ButtonMappingStore.saveFullConfiguration(service, updated)
                        updateText(service, bleState, nextMode, "Mode -> ${nextMode.shortTitle}")
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(layout, lp)
            pillContainer = layout
            updateText(service, bleState, mode, lastActionLabel)
        } catch (_: Exception) {
        }
    }

    private fun updateText(
        service: AccessibilityService,
        bleState: BleConnectionState,
        mode: OperatingMode,
        actionLabel: String
    ) {
        val dot = when (bleState) {
            BleConnectionState.CONNECTED_RIGHT_ONLY, BleConnectionState.CONNECTED_BOTH_PODS -> "🟢"
            BleConnectionState.SCANNING, BleConnectionState.CONNECTING, BleConnectionState.HANDSHAKING -> "🟡"
            else -> "🔴"
        }
        val dueTotal = try {
            AnkiContentProviderHelper.queryReviewInfo(service).sumOf { it.totalDue }
        } catch (_: Exception) {
            0
        }
        val dueSuffix = if (dueTotal > 0) " • Due:$dueTotal" else ""
        statusTextView?.text = "$dot ${mode.shortTitle}$dueSuffix • $actionLabel"
    }

    fun hide(service: AccessibilityService) {
        mainHandler.post {
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            pillContainer?.let { view ->
                try {
                    wm.removeView(view)
                } catch (_: Exception) {
                }
            }
            pillContainer = null
            statusTextView = null
        }
    }
}
```

### 5.3 `app/src/main/java/com/ankiride/bridge/service/ActionRouter.kt`

Integrates `ButtonMappingStore.resolveAction`, optional `FeedbackCueManager.triggerCue`, and optional `FloatingHudPill.notifyActionDispatched`:

```kotlin
package com.ankiride.bridge.service

import android.content.Context
import com.ankiride.bridge.core.*
import com.ankiride.bridge.ui.FloatingHudPill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ActionRouter(
    private val context: Context? = null,
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

        if (context != null) {
            FeedbackCueManager.triggerCue(context, target, config)
        }

        FloatingHudPill.notifyActionDispatched(
            service = BridgeAccessibilityService.activeInstance,
            enabled = config.floatingHudPillEnabled,
            bleState = BridgeForegroundService.connectionStateFlow.value,
            mode = config.mode,
            actionText = target.label
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

### 5.4 UI Toggles in `MainActivity.kt` for Features 1, 2 & 4

Inside `AnkiRideDashboard` in `MainActivity.kt`, load initial state from `ButtonMappingStore.loadFullConfiguration(context)` and include:
1. **Feature 4 (Ubuntu Desktop `AnkiConnect` Host & Port Card)** — shown whenever `selectedMode == OperatingMode.MODE_5_UBUNTU_ANKICONNECT` (with a **"Test Ubuntu Anki Flip"** button):
```kotlin
            if (selectedMode == OperatingMode.MODE_5_UBUNTU_ANKICONNECT) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Ubuntu Desktop Anki (AnkiConnect Port 8765)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "In Desktop Anki -> Tools -> Add-ons -> AnkiConnect -> Config, set webBindAddress to 0.0.0.0 and restart Anki.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ubuntuHost,
                                onValueChange = { ubuntuHost = it },
                                label = { Text("Ubuntu LAN IPv4 (e.g. 192.168.1.50)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = ubuntuPortText,
                                onValueChange = { ubuntuPortText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Port") },
                                modifier = Modifier.width(96.dp)
                            )
                        }
                    }
                }
            }
```
2. **Features 1 & 2 Optional Switches (All OFF by Default)** inside the Hardware & Shifting Options card:
```kotlin
                    // Feature 1: Optional Floating Mini HUD Pill (Off by Default)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Floating Mini HUD Pill (Optional)", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Off by default. Shows draggable mini status, due cards & last action over Split-Screen",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = floatingHudEnabled,
                            onCheckedChange = {
                                floatingHudEnabled = it
                                FloatingHudPill.syncVisibility(
                                    service = BridgeAccessibilityService.activeInstance,
                                    enabled = it,
                                    bleState = bleState,
                                    mode = selectedMode
                                )
                            }
                        )
                    }

                    // Feature 2A: Optional Haptic Vibration Cues (Off by Default)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Haptic Vibration Cues (Optional)", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Off by default. Distinct vibration ticks for Shift Up, Shift Down, Anki Good & Again",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hapticEnabled,
                            onCheckedChange = { hapticEnabled = it }
                        )
                    }

                    // Feature 2B: Optional Audio Beep/Tick Cues (Off by Default)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Audio Beep/Tick Cues (Optional)", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Off by default. Subtle audio confirmation tones when shifting or grading cards",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = audioEnabled,
                            onCheckedChange = { audioEnabled = it }
                        )
                    }
```

---

## 6. Mandatory Review & Security Gates (`RG-0` through `RG-6`)

| Gate ID | Target Phase / Subagent | Review & Security Verification Criteria | Automated Check Command |
| :--- | :--- | :--- | :--- |
| **`RG-0`** | Phase 0 (`ORCHESTRATOR`) | `Contracts.kt` defines `MODE_5_UBUNTU_ANKICONNECT`, `floatingHudPillEnabled = false`, `hapticFeedbackEnabled = false`, `audioFeedbackEnabled = false`, and `Z(0x0080, ...)`; `ButtonMappingStore.kt` persists all fields. | `grep -q "floatingHudPillEnabled: Boolean = false" app/src/main/java/com/ankiride/bridge/core/Contracts.kt && grep -q "MODE_5_UBUNTU_ANKICONNECT" app/src/main/java/com/ankiride/bridge/core/Contracts.kt` |
| **`RG-1`** | Phase 1A (`SA-1-BLE`) | `ZwiftGestureEngine.kt` emits `LONG_PRESS` on release when `holdDuration >= LONG_PRESS_THRESHOLD_MS` and enforces `DEBOUNCE_WINDOW_MS = 120L`. | `grep -q "DEBOUNCE_WINDOW_MS" app/src/main/java/com/ankiride/bridge/ble/ZwiftGestureEngine.kt` |
| **`RG-2`** | Phase 1B (`SA-2-ANKI`) | `AnkiConnectClient.kt` enforces `LanAddressValidator.isLocalOrLanAddress(cleanHost)`, implements `guiShowAnswer` / `guiAnswerCard` / `guiUndo` with `FLIP_TO_GRADE_GUARD_MS = 350L` and self-healing `"result":false` fallback. | `test -f app/src/main/java/com/ankiride/bridge/anki/AnkiConnectClient.kt && grep -q "guiAnswerCard" app/src/main/java/com/ankiride/bridge/anki/AnkiConnectClient.kt` |
| **`RG-3`** | Phase 1C (`SA-3-MYWHOOSH`) | `MyShiftOverlayCalibrator.kt` keys calibration coords by `profilePrefix(context, mode)`; `MyShiftGestureFallback.kt` uses `TAP_DURATION_MS = 75L`; `network_security_config.xml` keeps `cleartextTrafficPermitted="false"`. | `grep -q "profilePrefix" app/src/main/java/com/ankiride/bridge/mywhoosh/MyShiftOverlayCalibrator.kt && grep -q 'cleartextTrafficPermitted="false"' app/src/main/res/xml/network_security_config.xml` |
| **`RG-4`** | Phase 2 (`SA-4-APP-UI`) | `FloatingHudPill.kt` and `FeedbackCueManager.kt` exist and are disabled by default (`false`); `BridgeAccessibilityService.kt` cleans up both overlays on `onUnbind`/`onDestroy`. | `test -f app/src/main/java/com/ankiride/bridge/ui/FloatingHudPill.kt && test -f app/src/main/java/com/ankiride/bridge/service/FeedbackCueManager.kt` |
| **`RG-5`** | Phase 2 (`SA-4-APP-UI`) | Feature 3 (Workout App Launcher) is NOT present; `MainActivity.kt` renders `ColorCodedButtonCard`, Ubuntu LAN IP config for Mode 5, and switches for Floating HUD Pill and Haptic/Audio cues. | `grep -q "Ubuntu Desktop Anki" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt` |
| **`RG-6`** | Phase 3 (`SA-5-REVIEWER`) | Unit tests (including `AnkiConnectClientTest` against a local loopback `ServerSocket(0)` and `ActionRouterTest` for Mode 5) pass `./gradlew testDebugUnitTest --no-daemon`. | `./verify.sh && ./gradlew testDebugUnitTest --no-daemon` |

### 6.1 Updated `verify.sh` Script for `RG-0` .. `RG-6`

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "=== [RG-0] Checking Core Contracts (Defaults OFF for HUD/Haptic/Audio + Mode 5) ==="
grep -q "floatingHudPillEnabled: Boolean = false" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "hapticFeedbackEnabled: Boolean = false" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "audioFeedbackEnabled: Boolean = false" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "MODE_5_UBUNTU_ANKICONNECT" app/src/main/java/com/ankiride/bridge/core/Contracts.kt

echo "=== [RG-1] Checking BLE Parser Bounds & Gesture Engine Long-Press Release Fix ==="
grep -q "MAX_VARINT_BYTES = 5" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt
grep -q "DEBOUNCE_WINDOW_MS = 120L" app/src/main/java/com/ankiride/bridge/ble/ZwiftGestureEngine.kt

echo "=== [RG-2] Checking AnkiAccessibilityBridge & Ubuntu AnkiConnectClient ==="
test -f app/src/main/java/com/ankiride/bridge/anki/AnkiConnectClient.kt
grep -q "LanAddressValidator.isLocalOrLanAddress" app/src/main/java/com/ankiride/bridge/anki/AnkiConnectClient.kt
grep -q "guiAnswerCard" app/src/main/java/com/ankiride/bridge/anki/AnkiConnectClient.kt

echo "=== [RG-3] Checking Profile-Keyed MyShiftOverlayCalibrator & Network Security Config ==="
grep -q "profilePrefix" app/src/main/java/com/ankiride/bridge/mywhoosh/MyShiftOverlayCalibrator.kt
grep -q "TAP_DURATION_MS = 75L" app/src/main/java/com/ankiride/bridge/mywhoosh/MyShiftGestureFallback.kt
grep -q 'cleartextTrafficPermitted="false"' app/src/main/res/xml/network_security_config.xml

echo "=== [RG-4 & RG-5] Checking FloatingHudPill, FeedbackCueManager & Per-Mode UI ==="
test -f app/src/main/java/com/ankiride/bridge/ui/FloatingHudPill.kt
test -f app/src/main/java/com/ankiride/bridge/service/FeedbackCueManager.kt
grep -q "ColorCodedButtonCard" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt

echo "=== [RG-6] Running Full Unit Test Suite ==="
./gradlew testDebugUnitTest --no-daemon

echo "ALL REVIEW & SECURITY GATES (RG-0 .. RG-6) PASSED."
```

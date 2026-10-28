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
    val isPrimaryConfigurable: Boolean = true,
    val subBadgeGlyph: String = ""
) {
    Y(0x0040, "Right 'Y' (Top · Cyan)", "Y", 0xFF00AEEF, true, true, "≡"),
    Z(0x0080, "Right 'Z' (Left · Orange)", "Z", 0xFFFF6B35, true, true, "⏸"),
    A(0x0010, "Right 'A' (Right · Green)", "A", 0xFF8CC63F, true, true, "★"),
    B(0x0020, "Right 'B' (Bottom · Pink)", "B", 0xFFFF3385, true, true, "←"),
    SHIFT_UP_RIGHT(0x1000, "Right Bottom '+' (Shift Up)", "+", 0xFF0F172A, true, true, "⚙+"),
    SHIFT_DOWN_LEFT(0x0200, "Left Bottom '–' (Shift Down)", "–", 0xFF0F172A, false, true, "⚙-"),
    UP(0x0002, "Left D-Pad ^ (Up)", "^", 0xFF0F172A, false, true, "↑"),
    LEFT(0x0001, "Left D-Pad < (Left)", "<", 0xFF0F172A, false, true, "«"),
    RIGHT(0x0004, "Left D-Pad > (Right)", ">", 0xFF0F172A, false, true, "»"),
    DOWN(0x0008, "Left D-Pad v (Down)", "v", 0xFF0F172A, false, true, "↓"),
    // Secondary bit aliases for hardware firmware variants
    SHIFT_UP_LEFT(0x0100, "Left Shift Up (Bit 8)", "+L", 0xFF0F172A, false, false, "+"),
    SHIFT_DOWN_LEFT_ALT(0x0400, "Left '–' Button (Bit 10)", "–", 0xFF0F172A, false, false, "-"),
    POWERUP_LEFT(0x0800, "Left PowerUp (Bit 11)", "P", 0xFF0F172A, false, false, "P"),
    SHIFT_UP_RIGHT_ALT(0x2000, "Right '+' Button (Bit 13)", "+", 0xFF0F172A, true, false, "+"),
    SHIFT_DOWN_RIGHT(0x4000, "Right Aux Shift (Bit 14)", "–R", 0xFF0F172A, true, false, "-"),
    POWERUP_RIGHT(0x8000, "Right PowerUp (Bit 15)", "P", 0xFF0F172A, true, false, "P"),
    ONOFF_LEFT(0x10000, "Left On/Off (Bit 16)", "O", 0xFF0F172A, false, false, "O"),
    ONOFF_RIGHT(0x20000, "Right On/Off (Bit 17)", "O", 0xFF0F172A, true, false, "O");

    val canonicalButton: ClickButton
        get() = when (this) {
            SHIFT_UP_RIGHT_ALT, SHIFT_UP_LEFT, POWERUP_RIGHT, ONOFF_RIGHT -> SHIFT_UP_RIGHT
            SHIFT_DOWN_LEFT_ALT, SHIFT_DOWN_RIGHT, POWERUP_LEFT, ONOFF_LEFT -> SHIFT_DOWN_LEFT
            else -> this
        }

    val isShiftUpButton: Boolean
        get() = canonicalButton == SHIFT_UP_RIGHT

    val isShiftDownButton: Boolean
        get() = canonicalButton == SHIFT_DOWN_LEFT
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

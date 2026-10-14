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

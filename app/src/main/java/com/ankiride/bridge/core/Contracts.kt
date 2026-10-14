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

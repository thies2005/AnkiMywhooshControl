package com.ankiride.bridge.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class OperatingMode {
    MODE_1_TABLET_BOTH,
    MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
    MODE_3_ANKI_ONLY,
    MODE_4_MYWHOOSH_ONLY
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
    SINGLE_PRESS,
    DOUBLE_PRESS,
    LONG_PRESS
}

data class ButtonGestureEvent(
    val button: ClickButton,
    val pressType: PressType,
    val timestampMs: Long
)

enum class AnkiAction {
    FLIP_OR_EASE_1_AGAIN,
    FLIP_OR_EASE_2_HARD,
    FLIP_OR_EASE_3_GOOD,
    FLIP_OR_EASE_4_EASY,
    UNDO,
    BURY_CARD,
    REPLAY_AUDIO,
    PREV_HINT,
    NEXT_HINT
}

enum class MyWhooshAction {
    SHIFT_UP,
    SHIFT_DOWN,
    STEER_LEFT,
    STEER_RIGHT,
    TOGGLE_UI,
    EMOTE,
    CAMERA_VIEW,
    SELECT,
    BACK,
    POWER_UP,
    SCREENSHOT
}

data class AppConfiguration(
    val mode: OperatingMode = OperatingMode.MODE_1_TABLET_BOTH,
    val rightPodOnlyMode: Boolean = false,
    val leftPodAutoReconnectSec: Int = 55,
    val targetMyWhooshHost: String = "127.0.0.1",
    val targetMyWhooshPort: Int = 21587,
    val useAccessibilityTapFallbackForMyWhoosh: Boolean = false,
    val myShiftUpNormalizedX: Float = 0.92f,
    val myShiftUpNormalizedY: Float = 0.85f,
    val myShiftDownNormalizedX: Float = 0.84f,
    val myShiftDownNormalizedY: Float = 0.85f,
    val pinnedLeftPodMac: String? = null,
    val pinnedRightPodMac: String? = null
)

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data object Scanning : BleConnectionState
    data class Connected(
        val leftPodConnected: Boolean,
        val rightPodConnected: Boolean,
        val batteryPercent: Int? = null
    ) : BleConnectionState
    data class Error(val reason: String) : BleConnectionState
}

interface ClickBleController {
    val connectionState: StateFlow<BleConnectionState>
    val gestureEvents: SharedFlow<ButtonGestureEvent>
    fun start(config: AppConfiguration)
    fun stop()
}

interface AnkiActionDispatcher {
    suspend fun dispatch(action: AnkiAction): Boolean
}

interface MyWhooshActionDispatcher {
    suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean
}

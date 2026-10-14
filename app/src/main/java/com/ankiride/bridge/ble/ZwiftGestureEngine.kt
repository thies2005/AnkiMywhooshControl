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

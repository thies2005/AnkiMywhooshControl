package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickButton
import com.ankiride.bridge.core.PressType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ZwiftGestureEngine(
    private val scope: CoroutineScope,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val DOUBLE_PRESS_TIMEOUT_MS = 300L
        const val LONG_PRESS_TIMEOUT_MS = 500L
    }

    private val _gestureEvents = MutableSharedFlow<ButtonGestureEvent>(extraBufferCapacity = 64)
    val gestureEvents: SharedFlow<ButtonGestureEvent> = _gestureEvents.asSharedFlow()

    private val mutex = Mutex()
    private val pressedButtons = mutableMapOf<ClickButton, Long>()
    private val longPressTriggered = mutableSetOf<ClickButton>()
    private val doublePressTriggered = mutableSetOf<ClickButton>()
    private val longPressJobs = mutableMapOf<ClickButton, Job>()

    // For double-press detection
    private val pendingSinglePressJobs = mutableMapOf<ClickButton, Job>()
    private val lastReleaseTimestamps = mutableMapOf<ClickButton, Long>()

    // Configuration flags
    var rightPodOnlyMode: Boolean = false

    suspend fun onActiveButtonsChanged(activeButtons: Set<ClickButton>) {
        mutex.withLock {
            val now = timeProvider()

            // 1. Detect newly pressed buttons
            for (btn in activeButtons) {
                if (!pressedButtons.containsKey(btn)) {
                    onButtonDown(btn, now)
                }
            }

            // 2. Detect released buttons
            val currentlyPressed = pressedButtons.keys.toList()
            for (btn in currentlyPressed) {
                if (!activeButtons.contains(btn)) {
                    onButtonUp(btn, now)
                }
            }
        }
    }

    private fun onButtonDown(button: ClickButton, timestampMs: Long) {
        pressedButtons[button] = timestampMs
        longPressTriggered.remove(button)
        doublePressTriggered.remove(button)

        // If a single press was pending for this button, this second press within window is a DOUBLE PRESS!
        val pendingJob = pendingSinglePressJobs.remove(button)
        if (pendingJob != null) {
            pendingJob.cancel()
            val lastRelease = lastReleaseTimestamps[button] ?: 0L
            if (timestampMs - lastRelease <= DOUBLE_PRESS_TIMEOUT_MS) {
                doublePressTriggered.add(button)
                _gestureEvents.tryEmit(
                    ButtonGestureEvent(
                        button = button,
                        pressType = PressType.DOUBLE_PRESS,
                        timestampMs = timestampMs
                    )
                )
                return
            }
        }

        // Schedule long-press watchdog
        val job = scope.launch {
            delay(LONG_PRESS_TIMEOUT_MS)
            mutex.withLock {
                if (pressedButtons.containsKey(button)) {
                    longPressTriggered.add(button)
                    _gestureEvents.tryEmit(
                        ButtonGestureEvent(
                            button = button,
                            pressType = PressType.LONG_PRESS,
                            timestampMs = timeProvider()
                        )
                    )
                }
            }
        }
        longPressJobs[button] = job
    }

    private fun onButtonUp(button: ClickButton, timestampMs: Long) {
        pressedButtons.remove(button)
        val lpJob = longPressJobs.remove(button)
        lpJob?.cancel()

        val wasLongPressed = longPressTriggered.remove(button)
        if (wasLongPressed) {
            // Already fired LONG_PRESS; do not fire SINGLE_PRESS
            return
        }

        val wasDoublePressed = doublePressTriggered.remove(button)
        if (wasDoublePressed) {
            // Already handled as double press; do not schedule single press
            return
        }

        // Check if this button needs double-press disambiguation
        // SHIFT_UP_RIGHT needs double press detection in rightPodOnlyMode or for mode mapping
        val needsDoublePressCheck = (button == ClickButton.SHIFT_UP_RIGHT && rightPodOnlyMode)

        lastReleaseTimestamps[button] = timestampMs

        if (needsDoublePressCheck) {
            val singleJob = scope.launch {
                delay(DOUBLE_PRESS_TIMEOUT_MS)
                mutex.withLock {
                    pendingSinglePressJobs.remove(button)
                    _gestureEvents.tryEmit(
                        ButtonGestureEvent(
                            button = button,
                            pressType = PressType.SINGLE_PRESS,
                            timestampMs = timestampMs
                        )
                    )
                }
            }
            pendingSinglePressJobs[button] = singleJob
        } else {
            _gestureEvents.tryEmit(
                ButtonGestureEvent(
                    button = button,
                    pressType = PressType.SINGLE_PRESS,
                    timestampMs = timestampMs
                )
            )
        }
    }

    suspend fun reset() {
        mutex.withLock {
            longPressJobs.values.forEach { it.cancel() }
            longPressJobs.clear()
            pendingSinglePressJobs.values.forEach { it.cancel() }
            pendingSinglePressJobs.clear()
            pressedButtons.clear()
            longPressTriggered.clear()
            doublePressTriggered.clear()
            lastReleaseTimestamps.clear()
        }
    }
}

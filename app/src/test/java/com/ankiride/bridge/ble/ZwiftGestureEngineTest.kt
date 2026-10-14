package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickButton
import com.ankiride.bridge.core.PressType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZwiftGestureEngineTest {

    @Test
    fun testSinglePressDetection() = runTest {
        var simulatedTime = 1000L
        val engine = ZwiftGestureEngine(
            scope = this,
            timeProvider = { simulatedTime }
        )

        val events = mutableListOf<ButtonGestureEvent>()
        val collectJob = launch {
            engine.gestureEvents.collect { events.add(it) }
        }

        // Button A down
        engine.onActiveButtonsChanged(setOf(ClickButton.A))
        simulatedTime += 100L
        advanceTimeBy(100L)

        // Button A up
        engine.onActiveButtonsChanged(emptySet())
        advanceTimeBy(10L)

        assertEquals(1, events.size)
        assertEquals(ClickButton.A, events[0].button)
        assertEquals(PressType.SINGLE_PRESS, events[0].pressType)

        collectJob.cancel()
    }

    @Test
    fun testLongPressDetection() = runTest {
        var simulatedTime = 1000L
        val engine = ZwiftGestureEngine(
            scope = this,
            timeProvider = { simulatedTime }
        )

        val events = mutableListOf<ButtonGestureEvent>()
        val collectJob = launch {
            engine.gestureEvents.collect { events.add(it) }
        }

        // Button Z down
        engine.onActiveButtonsChanged(setOf(ClickButton.Z))

        // Hold for 550ms (>= 500ms)
        simulatedTime += 550L
        advanceTimeBy(550L)

        // Should have emitted LONG_PRESS
        assertEquals(1, events.size)
        assertEquals(ClickButton.Z, events[0].button)
        assertEquals(PressType.LONG_PRESS, events[0].pressType)

        // Button Z release: should NOT emit another single press
        engine.onActiveButtonsChanged(emptySet())
        simulatedTime += 50L
        advanceTimeBy(50L)

        assertEquals(1, events.size) // still only the LONG_PRESS

        collectJob.cancel()
    }

    @Test
    fun testDoublePressInRightPodOnlyMode() = runTest {
        var simulatedTime = 1000L
        val engine = ZwiftGestureEngine(
            scope = this,
            timeProvider = { simulatedTime }
        )
        engine.rightPodOnlyMode = true

        val events = mutableListOf<ButtonGestureEvent>()
        val collectJob = launch {
            engine.gestureEvents.collect { events.add(it) }
        }

        // First press down
        engine.onActiveButtonsChanged(setOf(ClickButton.SHIFT_UP_RIGHT))
        simulatedTime += 50L
        advanceTimeBy(50L)

        // First press up
        engine.onActiveButtonsChanged(emptySet())
        simulatedTime += 100L // 100ms gap (< 300ms)
        advanceTimeBy(100L)

        // Second press down within 300ms window
        engine.onActiveButtonsChanged(setOf(ClickButton.SHIFT_UP_RIGHT))
        simulatedTime += 50L
        advanceTimeBy(50L)

        // Should have emitted DOUBLE_PRESS immediately on second press down
        assertEquals(1, events.size)
        assertEquals(ClickButton.SHIFT_UP_RIGHT, events[0].button)
        assertEquals(PressType.DOUBLE_PRESS, events[0].pressType)

        // Second press up
        engine.onActiveButtonsChanged(emptySet())
        simulatedTime += 400L
        advanceTimeBy(400L)

        // No stray single press should be emitted
        assertEquals(1, events.size)

        collectJob.cancel()
    }

    @Test
    fun testSinglePressWhenDoublePressWindowExpires() = runTest {
        var simulatedTime = 1000L
        val engine = ZwiftGestureEngine(
            scope = this,
            timeProvider = { simulatedTime }
        )
        engine.rightPodOnlyMode = true

        val events = mutableListOf<ButtonGestureEvent>()
        val collectJob = launch {
            engine.gestureEvents.collect { events.add(it) }
        }

        // Press down
        engine.onActiveButtonsChanged(setOf(ClickButton.SHIFT_UP_RIGHT))
        simulatedTime += 50L
        advanceTimeBy(50L)

        // Release
        engine.onActiveButtonsChanged(emptySet())
        // Wait > 300ms without a second press
        simulatedTime += 350L
        advanceTimeBy(350L)

        assertEquals(1, events.size)
        assertEquals(ClickButton.SHIFT_UP_RIGHT, events[0].button)
        assertEquals(PressType.SINGLE_PRESS, events[0].pressType)

        collectJob.cancel()
    }
}

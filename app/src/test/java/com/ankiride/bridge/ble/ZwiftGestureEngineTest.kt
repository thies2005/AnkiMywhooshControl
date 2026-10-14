package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickButton
import com.ankiride.bridge.core.PressType
import org.junit.Assert.assertEquals
import org.junit.Test

class ZwiftGestureEngineTest {

    @Test
    fun testSingleClickDetection() {
        val events = mutableListOf<ButtonGestureEvent>()
        val engine = ZwiftGestureEngine { events.add(it) }

        // Button A pressed at t=1000
        engine.processButtonStates(mapOf(ClickButton.A to true), nowMs = 1000L)

        // Button A released at t=1100 (100ms hold < 450ms)
        engine.processButtonStates(mapOf(ClickButton.A to false), nowMs = 1100L)

        assertEquals(1, events.size)
        assertEquals(ClickButton.A, events[0].button)
        assertEquals(PressType.SINGLE_CLICK, events[0].type)
    }

    @Test
    fun testLongPressDetection() {
        val events = mutableListOf<ButtonGestureEvent>()
        val engine = ZwiftGestureEngine { events.add(it) }

        // Button Z pressed at t=1000
        engine.processButtonStates(mapOf(ClickButton.Z to true), nowMs = 1000L)

        // Button Z still held at t=1400 (400ms hold < 450ms) -> no event yet
        engine.processButtonStates(mapOf(ClickButton.Z to true), nowMs = 1400L)
        assertEquals(0, events.size)

        // Button Z still held at t=1500 (500ms hold >= 450ms) -> emits LONG_PRESS
        engine.processButtonStates(mapOf(ClickButton.Z to true), nowMs = 1500L)
        assertEquals(1, events.size)
        assertEquals(ClickButton.Z, events[0].button)
        assertEquals(PressType.LONG_PRESS, events[0].type)

        // Button Z released at t=1600 -> should NOT emit single click
        engine.processButtonStates(mapOf(ClickButton.Z to false), nowMs = 1600L)
        assertEquals(1, events.size)
    }

    @Test
    fun testMultipleButtonsIndependentTracking() {
        val events = mutableListOf<ButtonGestureEvent>()
        val engine = ZwiftGestureEngine { events.add(it) }

        // Button A and B pressed at t=1000
        engine.processButtonStates(mapOf(ClickButton.A to true, ClickButton.B to true), nowMs = 1000L)

        // Button A released at t=1150
        engine.processButtonStates(mapOf(ClickButton.A to false, ClickButton.B to true), nowMs = 1150L)

        assertEquals(1, events.size)
        assertEquals(ClickButton.A, events[0].button)
        assertEquals(PressType.SINGLE_CLICK, events[0].type)

        // Button B held past 450ms threshold at t=1500
        engine.processButtonStates(mapOf(ClickButton.A to false, ClickButton.B to true), nowMs = 1500L)

        assertEquals(2, events.size)
        assertEquals(ClickButton.B, events[1].button)
        assertEquals(PressType.LONG_PRESS, events[1].type)
    }
}

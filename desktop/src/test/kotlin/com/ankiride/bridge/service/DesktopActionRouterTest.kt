package com.ankiride.bridge.service

import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickButton
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.MappedTargetAction
import com.ankiride.bridge.core.ModeButtonBindingKey
import com.ankiride.bridge.core.MyWhooshAction
import com.ankiride.bridge.core.MyWhooshActionDispatcher
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.core.PressType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopActionRouterTest {

    private class FakeAnkiDispatcher : AnkiActionDispatcher {
        val dispatchedActions = mutableListOf<AnkiAction>()
        override fun dispatch(action: AnkiAction): Boolean {
            dispatchedActions.add(action)
            return true
        }
    }

    private class FakeMyWhooshDispatcher : MyWhooshActionDispatcher {
        val dispatchedActions = mutableListOf<MyWhooshAction>()
        override suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean {
            dispatchedActions.add(action)
            return true
        }
    }

    private lateinit var ankiDispatcher: FakeAnkiDispatcher
    private lateinit var myWhooshDispatcher: FakeMyWhooshDispatcher

    @Before
    fun setUp() {
        ankiDispatcher = FakeAnkiDispatcher()
        myWhooshDispatcher = FakeMyWhooshDispatcher()
        DiagnosticLogger.clear()
    }

    @Test
    fun testMode1TabletSplitScreenRouting() = runTest {
        val config = AppConfiguration(mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN)
        val router = DesktopActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        assertEquals("Ready", router.lastRoutedActionLabel.value)

        // Shifting
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_UP.label, router.lastRoutedActionLabel.value)

        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN.label, router.lastRoutedActionLabel.value)

        // Anki
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.LONG_PRESS))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.LONG_PRESS))

        // Left Pod D-Pad
        router.routeGesture(ButtonGestureEvent(ClickButton.LEFT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.RIGHT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.UP, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.DOWN, PressType.SINGLE_CLICK))

        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN,
                MyWhooshAction.STEER_LEFT,
                MyWhooshAction.STEER_RIGHT,
                MyWhooshAction.TOGGLE_CAMERA,
                MyWhooshAction.EMOTE
            ),
            myWhooshDispatcher.dispatchedActions
        )

        assertEquals(
            listOf(
                AnkiAction.FLIP_OR_AGAIN,
                AnkiAction.UNDO,
                AnkiAction.FLIP_OR_GOOD,
                AnkiAction.UNDO
            ),
            ankiDispatcher.dispatchedActions
        )

        val logs = DiagnosticLogger.logs.value.filter { it.category == LogCategory.ROUTER }
        assertTrue(logs.any { it.message.contains("[Split-Screen]") })
    }

    @Test
    fun testMode2TabletAnkiPhoneMyWhooshRouting() = runTest {
        val config = AppConfiguration(mode = OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH)
        val router = DesktopActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))

        testScheduler.advanceUntilIdle()

        assertEquals(listOf(MyWhooshAction.SHIFT_UP), myWhooshDispatcher.dispatchedActions)
        assertEquals(listOf(AnkiAction.FLIP_OR_GOOD), ankiDispatcher.dispatchedActions)

        val logs = DiagnosticLogger.logs.value.filter { it.category == LogCategory.ROUTER }
        assertTrue(logs.any { it.message.contains("[Tablet + Phone]") })
    }

    @Test
    fun testMode3AnkiOnlyRouting() = runTest {
        val config = AppConfiguration(mode = OperatingMode.MODE_3_ANKI_ONLY)
        val router = DesktopActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.LONG_PRESS))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.LONG_PRESS))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.LONG_PRESS))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.LONG_PRESS))
        router.routeGesture(ButtonGestureEvent(ClickButton.LEFT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.RIGHT, PressType.SINGLE_CLICK))

        testScheduler.advanceUntilIdle()

        assertTrue("Mode 3 must not dispatch to MyWhoosh", myWhooshDispatcher.dispatchedActions.isEmpty())
        assertEquals(
            listOf(
                AnkiAction.FLIP_OR_GOOD,
                AnkiAction.FLIP_OR_EASY,
                AnkiAction.FLIP_OR_GOOD,
                AnkiAction.UNDO,
                AnkiAction.FLIP_OR_AGAIN,
                AnkiAction.UNDO,
                AnkiAction.FLIP_OR_HARD,
                AnkiAction.UNDO,
                AnkiAction.UNDO,
                AnkiAction.FLIP_CARD
            ),
            ankiDispatcher.dispatchedActions
        )
    }

    @Test
    fun testMode4MyWhooshOnlyRouting() = runTest {
        val config = AppConfiguration(mode = OperatingMode.MODE_4_MYWHOOSH_ONLY)
        val router = DesktopActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_DOWN_LEFT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.LEFT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.RIGHT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.UP, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.DOWN, PressType.SINGLE_CLICK))

        testScheduler.advanceUntilIdle()

        assertTrue("Mode 4 must not dispatch to Anki", ankiDispatcher.dispatchedActions.isEmpty())
        assertEquals(
            listOf(
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN,
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN,
                MyWhooshAction.STEER_LEFT,
                MyWhooshAction.STEER_RIGHT,
                MyWhooshAction.STEER_LEFT,
                MyWhooshAction.STEER_RIGHT,
                MyWhooshAction.TOGGLE_CAMERA,
                MyWhooshAction.EMOTE
            ),
            myWhooshDispatcher.dispatchedActions
        )
    }

    @Test
    fun testMode5UbuntuAnkiConnectRouting() = runTest {
        val config = AppConfiguration(mode = OperatingMode.MODE_5_UBUNTU_ANKICONNECT)
        val router = DesktopActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))

        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(MyWhooshAction.SHIFT_UP, MyWhooshAction.SHIFT_DOWN),
            myWhooshDispatcher.dispatchedActions
        )
        assertEquals(
            listOf(AnkiAction.FLIP_OR_AGAIN, AnkiAction.FLIP_OR_GOOD),
            ankiDispatcher.dispatchedActions
        )

        val logs = DiagnosticLogger.logs.value.filter { it.category == LogCategory.ROUTER }
        assertTrue(logs.any { it.message.contains("[Ubuntu Anki + Ride]") })
    }

    @Test
    fun testCustomBindingsOverrideAndDisabledTarget() = runTest {
        val customBindings = mapOf(
            ModeButtonBindingKey(
                OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                ClickButton.B,
                PressType.SINGLE_CLICK
            ) to MappedTargetAction.MW_CAMERA,
            ModeButtonBindingKey(
                OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                ClickButton.A,
                PressType.SINGLE_CLICK
            ) to MappedTargetAction.NONE
        )
        val config = AppConfiguration(
            mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            customBindings = customBindings
        )
        val router = DesktopActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        // Custom override: B -> MyWhoosh TOGGLE_CAMERA
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_CAMERA.label, router.lastRoutedActionLabel.value)

        // Custom override: A -> NONE (disabled)
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.NONE.label, router.lastRoutedActionLabel.value)

        testScheduler.advanceUntilIdle()

        assertEquals(listOf(MyWhooshAction.TOGGLE_CAMERA), myWhooshDispatcher.dispatchedActions)
        assertTrue(ankiDispatcher.dispatchedActions.isEmpty())
    }

    @Test
    fun testFeedbackCueManagerAudioEnabledAndDisabled() {
        val configAudioDisabled = AppConfiguration(audioFeedbackEnabled = false)
        val configAudioEnabled = AppConfiguration(audioFeedbackEnabled = true)

        // NONE action should never trigger audio
        DesktopFeedbackCueManager.triggerCue(MappedTargetAction.NONE, configAudioEnabled)

        // Disabled audio config should return early
        DesktopFeedbackCueManager.triggerCue(MappedTargetAction.MW_SHIFT_UP, configAudioDisabled)

        // Enabled audio config triggers tone generation for all sound types without exception
        val targetsToTest = listOf(
            MappedTargetAction.MW_SHIFT_UP,
            MappedTargetAction.MW_SHIFT_DOWN,
            MappedTargetAction.ANKI_UNDO,
            MappedTargetAction.ANKI_FLIP_OR_AGAIN,
            MappedTargetAction.ANKI_FLIP_OR_GOOD,
            MappedTargetAction.ANKI_FLIP_OR_HARD,
            MappedTargetAction.MW_CAMERA
        )
        for (target in targetsToTest) {
            DesktopFeedbackCueManager.triggerCue(target, configAudioEnabled)
        }
    }
}

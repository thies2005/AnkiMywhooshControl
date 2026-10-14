package com.ankiride.bridge.service

import com.ankiride.bridge.core.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionRouterTest {

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
    }

    @Test
    fun testSinglePodDefaultCombinedModeRouting() = runTest {
        val currentConfig = AppConfiguration(
            mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            rightPodOnlyNoZwift = true,
            useZForShiftDownInSinglePodMode = true
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { currentConfig },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        // Right Pod Rocker: Single click = Shift Up, Long press = Shift Down
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.LONG_PRESS))

        // Top Z: Single click = Shift Down, Long press = Undo
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.LONG_PRESS))

        // A Button: Single click = Good, Long press = Easy
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.LONG_PRESS))

        // B Button: Hard
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))

        // Y Button: Single click = Again, Long press = Undo
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.LONG_PRESS))

        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN,
                MyWhooshAction.SHIFT_DOWN
            ),
            myWhooshDispatcher.dispatchedActions
        )

        assertEquals(
            listOf(
                AnkiAction.UNDO,
                AnkiAction.FLIP_OR_GOOD,
                AnkiAction.FLIP_OR_EASY,
                AnkiAction.FLIP_OR_HARD,
                AnkiAction.FLIP_OR_AGAIN,
                AnkiAction.UNDO
            ),
            ankiDispatcher.dispatchedActions
        )
    }

    @Test
    fun testDualPodCombinedModeRouting() = runTest {
        val config = AppConfiguration(
            mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            rightPodOnlyNoZwift = false
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        // Shifting
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_DOWN_LEFT, PressType.SINGLE_CLICK))

        // Right Pod diamond -> AnkiDroid
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.LONG_PRESS))

        // Left Pod D-Pad -> MyWhoosh
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
                AnkiAction.FLIP_OR_GOOD,
                AnkiAction.FLIP_OR_HARD,
                AnkiAction.FLIP_OR_AGAIN,
                AnkiAction.FLIP_OR_EASY,
                AnkiAction.UNDO
            ),
            ankiDispatcher.dispatchedActions
        )
    }

    @Test
    fun testMode3AnkiOnlyRouting() = runTest {
        val config = AppConfiguration(mode = OperatingMode.MODE_3_ANKI_ONLY)
        val router = ActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.LONG_PRESS))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.LONG_PRESS))
        router.routeGesture(ButtonGestureEvent(ClickButton.LEFT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.RIGHT, PressType.SINGLE_CLICK))

        testScheduler.advanceUntilIdle()

        assertTrue(myWhooshDispatcher.dispatchedActions.isEmpty())
        assertEquals(
            listOf(
                AnkiAction.FLIP_OR_GOOD,
                AnkiAction.FLIP_OR_HARD,
                AnkiAction.FLIP_OR_AGAIN,
                AnkiAction.UNDO,
                AnkiAction.FLIP_OR_EASY,
                AnkiAction.UNDO,
                AnkiAction.UNDO,
                AnkiAction.FLIP_CARD
            ),
            ankiDispatcher.dispatchedActions
        )
    }

    @Test
    fun testMode4MyWhooshOnlyRouting() = runTest {
        val config = AppConfiguration(
            mode = OperatingMode.MODE_4_MYWHOOSH_ONLY,
            rightPodOnlyNoZwift = false
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_DOWN_LEFT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.LEFT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.RIGHT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.UP, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.DOWN, PressType.SINGLE_CLICK))

        testScheduler.advanceUntilIdle()

        assertTrue(ankiDispatcher.dispatchedActions.isEmpty())
        assertEquals(
            listOf(
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN,
                MyWhooshAction.UI_CONFIRM,
                MyWhooshAction.UI_BACK,
                MyWhooshAction.HUD_TOGGLE,
                MyWhooshAction.USE_POWERUP,
                MyWhooshAction.STEER_LEFT,
                MyWhooshAction.STEER_RIGHT,
                MyWhooshAction.TOGGLE_CAMERA,
                MyWhooshAction.EMOTE
            ),
            myWhooshDispatcher.dispatchedActions
        )
    }

    @Test
    fun testCustomBindingsOverrideAnkiAction() = runTest {
        // Normally SHIFT_UP_RIGHT in SinglePodCombinedMode maps to MyWhooshAction.SHIFT_UP
        // Override it with AnkiAction.FLIP_OR_AGAIN
        val customBindings = mapOf(
            ButtonBindingKey(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK) to MappedTargetAction.ANKI_FLIP_OR_AGAIN
        )
        val config = AppConfiguration(
            mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            rightPodOnlyNoZwift = true,
            customBindings = customBindings
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))
        testScheduler.advanceUntilIdle()

        // Should dispatch AnkiAction.FLIP_OR_AGAIN and NO MyWhooshAction
        assertEquals(listOf(AnkiAction.FLIP_OR_AGAIN), ankiDispatcher.dispatchedActions)
        assertTrue(myWhooshDispatcher.dispatchedActions.isEmpty())
    }

    @Test
    fun testCustomBindingsOverrideMyWhooshAction() = runTest {
        // Normally ClickButton.A in SinglePodCombinedMode maps to AnkiAction.FLIP_OR_GOOD
        // Override it with MappedTargetAction.MW_CAMERA (MyWhooshAction.TOGGLE_CAMERA)
        val customBindings = mapOf(
            ButtonBindingKey(ClickButton.A, PressType.SINGLE_CLICK) to MappedTargetAction.MW_CAMERA
        )
        val config = AppConfiguration(
            mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            rightPodOnlyNoZwift = true,
            customBindings = customBindings
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        testScheduler.advanceUntilIdle()

        // Should dispatch MyWhooshAction.TOGGLE_CAMERA and NO AnkiAction
        assertEquals(listOf(MyWhooshAction.TOGGLE_CAMERA), myWhooshDispatcher.dispatchedActions)
        assertTrue(ankiDispatcher.dispatchedActions.isEmpty())
    }

    @Test
    fun testCustomBindingsOverrideWithDisabledNone() = runTest {
        // Bind ClickButton.A to NONE (disabled)
        val customBindings = mapOf(
            ButtonBindingKey(ClickButton.A, PressType.SINGLE_CLICK) to MappedTargetAction.NONE
        )
        val config = AppConfiguration(
            mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            rightPodOnlyNoZwift = true,
            customBindings = customBindings
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        testScheduler.advanceUntilIdle()

        // Neither Anki nor MyWhoosh should receive an action
        assertTrue(ankiDispatcher.dispatchedActions.isEmpty())
        assertTrue(myWhooshDispatcher.dispatchedActions.isEmpty())
    }

    @Test
    fun testCustomBindingsMixedWithDefaultRouting() = runTest {
        // Bind B to MW_POWERUP and Y to ANKI_UNDO
        val customBindings = mapOf(
            ButtonBindingKey(ClickButton.B, PressType.SINGLE_CLICK) to MappedTargetAction.MW_POWERUP,
            ButtonBindingKey(ClickButton.Y, PressType.SINGLE_CLICK) to MappedTargetAction.ANKI_UNDO
        )
        val config = AppConfiguration(
            mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            rightPodOnlyNoZwift = true,
            customBindings = customBindings
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        // Custom override: B -> MyWhoosh USE_POWERUP
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        // Default: A -> Anki FLIP_OR_GOOD
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        // Custom override: Y -> Anki UNDO
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        // Default: SHIFT_UP_RIGHT -> MyWhoosh SHIFT_UP
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))

        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(MyWhooshAction.USE_POWERUP, MyWhooshAction.SHIFT_UP),
            myWhooshDispatcher.dispatchedActions
        )
        assertEquals(
            listOf(AnkiAction.FLIP_OR_GOOD, AnkiAction.UNDO),
            ankiDispatcher.dispatchedActions
        )
    }

    @Test
    fun testDynamicConfigSupplierUpdatesBindings() = runTest {
        var currentConfig = AppConfiguration(
            mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            rightPodOnlyNoZwift = true
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { currentConfig },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        // Initial default: A -> FLIP_OR_GOOD
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(AnkiAction.FLIP_OR_GOOD), ankiDispatcher.dispatchedActions)

        // Dynamically update config with custom binding for A
        currentConfig = currentConfig.copy(
            customBindings = mapOf(
                ButtonBindingKey(ClickButton.A, PressType.SINGLE_CLICK) to MappedTargetAction.MW_STEER_LEFT
            )
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(MyWhooshAction.STEER_LEFT), myWhooshDispatcher.dispatchedActions)
    }

    @Test
    fun testMode4MyWhooshOnlyRoutingSinglePodDefault() = runTest {
        val config = AppConfiguration(
            mode = OperatingMode.MODE_4_MYWHOOSH_ONLY,
            rightPodOnlyNoZwift = true
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        // In 1-pod Mode 4: A & + shift UP, B & Z & - shift DOWN
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.LONG_PRESS))

        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN,
                MyWhooshAction.SHIFT_DOWN,
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN
            ),
            myWhooshDispatcher.dispatchedActions
        )
    }
}

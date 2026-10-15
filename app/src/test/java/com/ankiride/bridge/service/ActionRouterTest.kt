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
    fun testRightPodFourColorSplitCombinedModeRouting() = runTest {
        val currentConfig = AppConfiguration(
            mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            rightPodOnlyNoZwift = true
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

        // Top Orange Z: Single click = Shift Up
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))

        // Bottom Blue A: Single click = Shift Down
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))

        // Left Yellow Y: Single click = Again, Long press = Undo
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.LONG_PRESS))

        // Right Green B: Single click = Good, Long press = Undo
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.LONG_PRESS))

        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN,
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN
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

        // Right Pod 4-color split
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))

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
                AnkiAction.FLIP_OR_GOOD
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
                AnkiAction.FLIP_OR_GOOD,
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
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
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
    fun testCustomBindingsOverrideAnkiAction() = runTest {
        val customBindings = mapOf(
            ModeButtonBindingKey(
                OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                ClickButton.SHIFT_UP_RIGHT,
                PressType.SINGLE_CLICK
            ) to MappedTargetAction.ANKI_FLIP_OR_AGAIN
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

        assertEquals(listOf(AnkiAction.FLIP_OR_AGAIN), ankiDispatcher.dispatchedActions)
        assertTrue(myWhooshDispatcher.dispatchedActions.isEmpty())
    }

    @Test
    fun testCustomBindingsOverrideMyWhooshAction() = runTest {
        // Normally ClickButton.Y maps to AnkiAction.FLIP_OR_AGAIN
        // Override it with MappedTargetAction.MW_CAMERA
        val customBindings = mapOf(
            ModeButtonBindingKey(
                OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                ClickButton.Y,
                PressType.SINGLE_CLICK
            ) to MappedTargetAction.MW_CAMERA
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

        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(MyWhooshAction.TOGGLE_CAMERA), myWhooshDispatcher.dispatchedActions)
        assertTrue(ankiDispatcher.dispatchedActions.isEmpty())
    }

    @Test
    fun testCustomBindingsOverrideWithDisabledNone() = runTest {
        val customBindings = mapOf(
            ModeButtonBindingKey(
                OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                ClickButton.A,
                PressType.SINGLE_CLICK
            ) to MappedTargetAction.NONE
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

        assertTrue(ankiDispatcher.dispatchedActions.isEmpty())
        assertTrue(myWhooshDispatcher.dispatchedActions.isEmpty())
    }

    @Test
    fun testCustomBindingsMixedWithDefaultRouting() = runTest {
        val customBindings = mapOf(
            ModeButtonBindingKey(
                OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                ClickButton.B,
                PressType.SINGLE_CLICK
            ) to MappedTargetAction.MW_CAMERA,
            ModeButtonBindingKey(
                OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                ClickButton.Y,
                PressType.SINGLE_CLICK
            ) to MappedTargetAction.ANKI_UNDO
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

        // Custom override: B -> MyWhoosh TOGGLE_CAMERA
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        // Default: A -> Shift Down
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))
        // Custom override: Y -> Anki UNDO
        router.routeGesture(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_CLICK))
        // Default: SHIFT_UP_RIGHT -> MyWhoosh SHIFT_UP
        router.routeGesture(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))

        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(MyWhooshAction.TOGGLE_CAMERA, MyWhooshAction.SHIFT_DOWN, MyWhooshAction.SHIFT_UP),
            myWhooshDispatcher.dispatchedActions
        )
        assertEquals(
            listOf(AnkiAction.UNDO),
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

        // Initial default: B -> FLIP_OR_GOOD
        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(AnkiAction.FLIP_OR_GOOD), ankiDispatcher.dispatchedActions)

        // Dynamically update config with custom binding for B
        currentConfig = currentConfig.copy(
            customBindings = mapOf(
                ModeButtonBindingKey(
                    OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                    ClickButton.B,
                    PressType.SINGLE_CLICK
                ) to MappedTargetAction.MW_STEER_LEFT
            )
        )

        router.routeGesture(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_CLICK))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(MyWhooshAction.STEER_LEFT), myWhooshDispatcher.dispatchedActions)
    }

    @Test
    fun testMode5UbuntuAnkiConnectRouting() = runTest {
        val config = AppConfiguration(
            mode = OperatingMode.MODE_5_UBUNTU_ANKICONNECT,
            rightPodOnlyNoZwift = true
        )
        val router = ActionRouter(
            scope = this,
            configSupplier = { config },
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher
        )

        // Z -> Shift Up, A -> Shift Down
        router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
        router.routeGesture(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_CLICK))

        // Y -> FLIP_OR_AGAIN, B -> FLIP_OR_GOOD
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
    }
}

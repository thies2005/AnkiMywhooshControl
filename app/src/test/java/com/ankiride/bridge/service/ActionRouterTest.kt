package com.ankiride.bridge.service

import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.BleConnectionState
import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickBleController
import com.ankiride.bridge.core.ClickButton
import com.ankiride.bridge.core.MyWhooshAction
import com.ankiride.bridge.core.MyWhooshActionDispatcher
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.core.PressType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionRouterTest {

    private class FakeBleController : ClickBleController {
        override val connectionState: StateFlow<BleConnectionState> = MutableStateFlow(BleConnectionState.Disconnected)
        val eventFlow = MutableSharedFlow<ButtonGestureEvent>(extraBufferCapacity = 64)
        override val gestureEvents: SharedFlow<ButtonGestureEvent> = eventFlow
        override fun start(config: AppConfiguration) {}
        override fun stop() {}
    }

    private class FakeAnkiDispatcher : AnkiActionDispatcher {
        val dispatchedActions = mutableListOf<AnkiAction>()
        override suspend fun dispatch(action: AnkiAction): Boolean {
            dispatchedActions.add(action)
            return true
        }
    }

    private class FakeMyWhooshDispatcher : MyWhooshActionDispatcher {
        val dispatchedActions = mutableListOf<MyWhooshAction>()
        val receivedConfigs = mutableListOf<AppConfiguration>()
        override suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean {
            dispatchedActions.add(action)
            receivedConfigs.add(config)
            return true
        }
    }

    private lateinit var bleController: FakeBleController
    private lateinit var ankiDispatcher: FakeAnkiDispatcher
    private lateinit var myWhooshDispatcher: FakeMyWhooshDispatcher

    @Before
    fun setUp() {
        bleController = FakeBleController()
        ankiDispatcher = FakeAnkiDispatcher()
        myWhooshDispatcher = FakeMyWhooshDispatcher()
    }

    @Test
    fun testMode1TabletBothRouting() = runBlocking {
        val router = ActionRouter(
            bleController = bleController,
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher,
            config = AppConfiguration(mode = OperatingMode.MODE_1_TABLET_BOTH)
        )

        // Shifting
        router.routeEvent(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_PRESS, 100L))
        router.routeEvent(ButtonGestureEvent(ClickButton.SHIFT_DOWN_LEFT, PressType.SINGLE_PRESS, 200L))

        // Review buttons
        router.routeEvent(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_PRESS, 300L))
        router.routeEvent(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_PRESS, 400L))
        router.routeEvent(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_PRESS, 500L))
        router.routeEvent(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_PRESS, 600L))
        router.routeEvent(ButtonGestureEvent(ClickButton.Z, PressType.LONG_PRESS, 700L))

        // D-pad steering & actions
        router.routeEvent(ButtonGestureEvent(ClickButton.LEFT, PressType.SINGLE_PRESS, 800L))
        router.routeEvent(ButtonGestureEvent(ClickButton.RIGHT, PressType.SINGLE_PRESS, 900L))
        router.routeEvent(ButtonGestureEvent(ClickButton.UP, PressType.SINGLE_PRESS, 1000L))
        router.routeEvent(ButtonGestureEvent(ClickButton.DOWN, PressType.SINGLE_PRESS, 1100L))

        assertEquals(
            listOf(
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SHIFT_DOWN,
                MyWhooshAction.STEER_LEFT,
                MyWhooshAction.STEER_RIGHT,
                MyWhooshAction.TOGGLE_UI,
                MyWhooshAction.EMOTE
            ),
            myWhooshDispatcher.dispatchedActions
        )

        assertEquals(
            listOf(
                AnkiAction.FLIP_OR_EASE_3_GOOD,
                AnkiAction.FLIP_OR_EASE_2_HARD,
                AnkiAction.FLIP_OR_EASE_1_AGAIN,
                AnkiAction.FLIP_OR_EASE_4_EASY,
                AnkiAction.UNDO
            ),
            ankiDispatcher.dispatchedActions
        )
    }

    @Test
    fun testMode1RightPodOnlyDoublePressShiftDown() = runBlocking {
        val router = ActionRouter(
            bleController = bleController,
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher,
            config = AppConfiguration(mode = OperatingMode.MODE_1_TABLET_BOTH, rightPodOnlyMode = true)
        )

        router.routeEvent(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.DOUBLE_PRESS, 100L))
        assertEquals(listOf(MyWhooshAction.SHIFT_DOWN), myWhooshDispatcher.dispatchedActions)
        assertTrue(ankiDispatcher.dispatchedActions.isEmpty())
    }

    @Test
    fun testMode2TabletAnkiPhoneMyWhooshRouting() = runBlocking {
        val phoneIp = "192.168.1.105"
        val router = ActionRouter(
            bleController = bleController,
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher,
            config = AppConfiguration(
                mode = OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
                targetMyWhooshHost = phoneIp
            )
        )

        router.routeEvent(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_PRESS, 100L))
        router.routeEvent(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_PRESS, 200L))

        assertEquals(listOf(MyWhooshAction.SHIFT_UP), myWhooshDispatcher.dispatchedActions)
        assertEquals(phoneIp, myWhooshDispatcher.receivedConfigs.first().targetMyWhooshHost)
        assertEquals(listOf(AnkiAction.FLIP_OR_EASE_3_GOOD), ankiDispatcher.dispatchedActions)
    }

    @Test
    fun testMode3AnkiOnlyRouting() = runBlocking {
        val router = ActionRouter(
            bleController = bleController,
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher,
            config = AppConfiguration(mode = OperatingMode.MODE_3_ANKI_ONLY)
        )

        router.routeEvent(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_PRESS, 100L))
        router.routeEvent(ButtonGestureEvent(ClickButton.SHIFT_DOWN_LEFT, PressType.SINGLE_PRESS, 200L))
        router.routeEvent(ButtonGestureEvent(ClickButton.LEFT, PressType.SINGLE_PRESS, 300L))
        router.routeEvent(ButtonGestureEvent(ClickButton.RIGHT, PressType.SINGLE_PRESS, 400L))
        router.routeEvent(ButtonGestureEvent(ClickButton.UP, PressType.SINGLE_PRESS, 500L))
        router.routeEvent(ButtonGestureEvent(ClickButton.DOWN, PressType.SINGLE_PRESS, 600L))
        router.routeEvent(ButtonGestureEvent(ClickButton.Z, PressType.LONG_PRESS, 700L))

        // MyWhoosh dispatcher must NEVER be invoked in Mode 3
        assertTrue(myWhooshDispatcher.dispatchedActions.isEmpty())

        assertEquals(
            listOf(
                AnkiAction.REPLAY_AUDIO,
                AnkiAction.UNDO,
                AnkiAction.PREV_HINT,
                AnkiAction.NEXT_HINT,
                AnkiAction.REPLAY_AUDIO,
                AnkiAction.BURY_CARD,
                AnkiAction.UNDO
            ),
            ankiDispatcher.dispatchedActions
        )
    }

    @Test
    fun testMode4MyWhooshOnlyRouting() = runBlocking {
        val router = ActionRouter(
            bleController = bleController,
            ankiDispatcher = ankiDispatcher,
            myWhooshDispatcher = myWhooshDispatcher,
            config = AppConfiguration(mode = OperatingMode.MODE_4_MYWHOOSH_ONLY)
        )

        router.routeEvent(ButtonGestureEvent(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_PRESS, 100L))
        router.routeEvent(ButtonGestureEvent(ClickButton.A, PressType.SINGLE_PRESS, 200L))
        router.routeEvent(ButtonGestureEvent(ClickButton.B, PressType.SINGLE_PRESS, 300L))
        router.routeEvent(ButtonGestureEvent(ClickButton.Y, PressType.SINGLE_PRESS, 400L))
        router.routeEvent(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_PRESS, 500L))
        router.routeEvent(ButtonGestureEvent(ClickButton.Z, PressType.LONG_PRESS, 600L))
        router.routeEvent(ButtonGestureEvent(ClickButton.UP, PressType.SINGLE_PRESS, 700L))
        router.routeEvent(ButtonGestureEvent(ClickButton.DOWN, PressType.SINGLE_PRESS, 800L))

        // Anki dispatcher must NEVER be invoked in Mode 4
        assertTrue(ankiDispatcher.dispatchedActions.isEmpty())

        assertEquals(
            listOf(
                MyWhooshAction.SHIFT_UP,
                MyWhooshAction.SELECT,
                MyWhooshAction.BACK,
                MyWhooshAction.TOGGLE_UI,
                MyWhooshAction.POWER_UP,
                MyWhooshAction.SCREENSHOT,
                MyWhooshAction.CAMERA_VIEW,
                MyWhooshAction.EMOTE
            ),
            myWhooshDispatcher.dispatchedActions
        )
    }
}

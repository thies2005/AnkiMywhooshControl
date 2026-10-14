package com.ankiride.bridge.service

import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickBleController
import com.ankiride.bridge.core.ClickButton
import com.ankiride.bridge.core.MyWhooshAction
import com.ankiride.bridge.core.MyWhooshActionDispatcher
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.core.PressType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ActionRouter(
    private val bleController: ClickBleController,
    private val ankiDispatcher: AnkiActionDispatcher,
    private val myWhooshDispatcher: MyWhooshActionDispatcher,
    private var config: AppConfiguration = AppConfiguration()
) {
    private var routingJob: Job? = null

    fun updateConfig(newConfig: AppConfiguration) {
        this.config = newConfig
    }

    fun start(scope: CoroutineScope) {
        routingJob?.cancel()
        routingJob = scope.launch {
            bleController.gestureEvents.collect { event ->
                routeEvent(event)
            }
        }
    }

    fun stop() {
        routingJob?.cancel()
        routingJob = null
    }

    suspend fun routeEvent(event: ButtonGestureEvent): Boolean {
        return when (config.mode) {
            OperatingMode.MODE_1_TABLET_BOTH,
            OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH -> routeModeBoth(event)
            OperatingMode.MODE_3_ANKI_ONLY -> routeModeAnkiOnly(event)
            OperatingMode.MODE_4_MYWHOOSH_ONLY -> routeModeMyWhooshOnly(event)
        }
    }

    private suspend fun routeModeBoth(event: ButtonGestureEvent): Boolean {
        return when (event.button) {
            ClickButton.SHIFT_UP_RIGHT -> {
                if (event.pressType == PressType.DOUBLE_PRESS && config.rightPodOnlyMode) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config)
                } else if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_UP, config)
                } else false
            }
            ClickButton.SHIFT_DOWN_LEFT -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config)
                } else false
            }
            ClickButton.A -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASE_3_GOOD)
                } else false
            }
            ClickButton.B -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASE_2_HARD)
                } else false
            }
            ClickButton.Y -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASE_1_AGAIN)
                } else false
            }
            ClickButton.Z -> {
                when (event.pressType) {
                    PressType.SINGLE_PRESS -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASE_4_EASY)
                    PressType.LONG_PRESS -> ankiDispatcher.dispatch(AnkiAction.UNDO)
                    else -> false
                }
            }
            ClickButton.LEFT -> {
                myWhooshDispatcher.dispatch(MyWhooshAction.STEER_LEFT, config)
            }
            ClickButton.RIGHT -> {
                myWhooshDispatcher.dispatch(MyWhooshAction.STEER_RIGHT, config)
            }
            ClickButton.UP -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.TOGGLE_UI, config)
                } else false
            }
            ClickButton.DOWN -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.EMOTE, config)
                } else false
            }
            else -> false
        }
    }

    private suspend fun routeModeAnkiOnly(event: ButtonGestureEvent): Boolean {
        // Disables MyWhoosh output completely
        return when (event.button) {
            ClickButton.SHIFT_UP_RIGHT -> {
                if (event.pressType == PressType.DOUBLE_PRESS && config.rightPodOnlyMode) {
                    ankiDispatcher.dispatch(AnkiAction.BURY_CARD)
                } else if (event.pressType == PressType.SINGLE_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.REPLAY_AUDIO)
                } else false
            }
            ClickButton.SHIFT_DOWN_LEFT -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.UNDO)
                } else false
            }
            ClickButton.A -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASE_3_GOOD)
                } else false
            }
            ClickButton.B -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASE_2_HARD)
                } else false
            }
            ClickButton.Y -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASE_1_AGAIN)
                } else false
            }
            ClickButton.Z -> {
                when (event.pressType) {
                    PressType.SINGLE_PRESS -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASE_4_EASY)
                    PressType.LONG_PRESS -> ankiDispatcher.dispatch(AnkiAction.UNDO)
                    else -> false
                }
            }
            ClickButton.LEFT -> ankiDispatcher.dispatch(AnkiAction.PREV_HINT)
            ClickButton.RIGHT -> ankiDispatcher.dispatch(AnkiAction.NEXT_HINT)
            ClickButton.UP -> ankiDispatcher.dispatch(AnkiAction.REPLAY_AUDIO)
            ClickButton.DOWN -> ankiDispatcher.dispatch(AnkiAction.BURY_CARD)
            else -> false
        }
    }

    private suspend fun routeModeMyWhooshOnly(event: ButtonGestureEvent): Boolean {
        // Disables AnkiDroid clicking completely
        return when (event.button) {
            ClickButton.SHIFT_UP_RIGHT -> {
                if (event.pressType == PressType.DOUBLE_PRESS && config.rightPodOnlyMode) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config)
                } else if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_UP, config)
                } else false
            }
            ClickButton.SHIFT_DOWN_LEFT -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config)
                } else false
            }
            ClickButton.A -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.SELECT, config)
                } else false
            }
            ClickButton.B -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.BACK, config)
                } else false
            }
            ClickButton.Y -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.TOGGLE_UI, config)
                } else false
            }
            ClickButton.Z -> {
                when (event.pressType) {
                    PressType.SINGLE_PRESS -> myWhooshDispatcher.dispatch(MyWhooshAction.POWER_UP, config)
                    PressType.LONG_PRESS -> myWhooshDispatcher.dispatch(MyWhooshAction.SCREENSHOT, config)
                    else -> false
                }
            }
            ClickButton.LEFT -> myWhooshDispatcher.dispatch(MyWhooshAction.STEER_LEFT, config)
            ClickButton.RIGHT -> myWhooshDispatcher.dispatch(MyWhooshAction.STEER_RIGHT, config)
            ClickButton.UP -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.CAMERA_VIEW, config)
                } else false
            }
            ClickButton.DOWN -> {
                if (event.pressType == PressType.SINGLE_PRESS) {
                    myWhooshDispatcher.dispatch(MyWhooshAction.EMOTE, config)
                } else false
            }
            else -> false
        }
    }
}

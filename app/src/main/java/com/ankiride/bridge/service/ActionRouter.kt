package com.ankiride.bridge.service

import com.ankiride.bridge.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ActionRouter(
    private val scope: CoroutineScope,
    private val configSupplier: () -> AppConfiguration,
    private val ankiDispatcher: AnkiActionDispatcher,
    private val myWhooshDispatcher: MyWhooshActionDispatcher
) {

    fun routeGesture(event: ButtonGestureEvent) {
        val config = configSupplier()

        when (config.mode) {
            OperatingMode.MODE_3_ANKI_ONLY -> {
                val ankiAction = mapAnkiOnlyMode(event) ?: return
                ankiDispatcher.dispatch(ankiAction)
            }
            OperatingMode.MODE_4_MYWHOOSH_ONLY -> {
                val mwAction = mapMyWhooshOnlyMode(event, config) ?: return
                scope.launch { myWhooshDispatcher.dispatch(mwAction, config) }
            }
            OperatingMode.MODE_1_TABLET_SPLIT_SCREEN, OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH -> {
                if (config.rightPodOnlyNoZwift) {
                    routeSinglePodCombinedMode(event, config)
                } else {
                    routeDualPodCombinedMode(event, config)
                }
            }
        }
    }

    private fun routeSinglePodCombinedMode(event: ButtonGestureEvent, config: AppConfiguration) {
        when (event.button) {
            ClickButton.SHIFT_UP_RIGHT -> {
                val mwAction = if (event.type == PressType.LONG_PRESS) {
                    MyWhooshAction.SHIFT_DOWN
                } else {
                    MyWhooshAction.SHIFT_UP
                }
                scope.launch { myWhooshDispatcher.dispatch(mwAction, config) }
            }
            ClickButton.Z -> {
                if (event.type == PressType.LONG_PRESS) {
                    ankiDispatcher.dispatch(AnkiAction.UNDO)
                } else if (config.useZForShiftDownInSinglePodMode) {
                    scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
                } else {
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASY)
                }
            }
            ClickButton.A -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.FLIP_OR_EASY else AnkiAction.FLIP_OR_GOOD
                ankiDispatcher.dispatch(action)
            }
            ClickButton.B -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            ClickButton.Y -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_AGAIN
                ankiDispatcher.dispatch(action)
            }
            else -> {}
        }
    }

    private fun routeDualPodCombinedMode(event: ButtonGestureEvent, config: AppConfiguration) {
        when (event.button) {
            // Right Pod A/B/Y/Z -> AnkiDroid
            ClickButton.A -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_GOOD)
            ClickButton.B -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            ClickButton.Y -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_AGAIN)
            ClickButton.Z -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_EASY
                ankiDispatcher.dispatch(action)
            }
            // Shift Rockers & Left Pod D-Pad -> MyWhoosh
            ClickButton.SHIFT_UP_RIGHT, ClickButton.SHIFT_UP_LEFT -> {
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_UP, config) }
            }
            ClickButton.SHIFT_DOWN_LEFT, ClickButton.SHIFT_DOWN_RIGHT -> {
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
            }
            ClickButton.LEFT -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.STEER_LEFT, config) }
            ClickButton.RIGHT -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.STEER_RIGHT, config) }
            ClickButton.UP -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.TOGGLE_CAMERA, config) }
            ClickButton.DOWN -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.EMOTE, config) }
            else -> {}
        }
    }

    private fun mapAnkiOnlyMode(event: ButtonGestureEvent): AnkiAction? {
        return when (event.button) {
            ClickButton.A, ClickButton.SHIFT_UP_RIGHT -> AnkiAction.FLIP_OR_GOOD
            ClickButton.B -> AnkiAction.FLIP_OR_HARD
            ClickButton.Y -> if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_AGAIN
            ClickButton.Z -> if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_EASY
            ClickButton.SHIFT_DOWN_LEFT, ClickButton.LEFT -> AnkiAction.UNDO
            ClickButton.RIGHT, ClickButton.UP, ClickButton.DOWN -> AnkiAction.FLIP_CARD
            else -> null
        }
    }

    private fun mapMyWhooshOnlyMode(event: ButtonGestureEvent, config: AppConfiguration): MyWhooshAction? {
        return when (event.button) {
            ClickButton.SHIFT_UP_RIGHT, ClickButton.SHIFT_UP_LEFT -> {
                if (config.rightPodOnlyNoZwift && event.type == PressType.LONG_PRESS) MyWhooshAction.SHIFT_DOWN
                else MyWhooshAction.SHIFT_UP
            }
            ClickButton.SHIFT_DOWN_LEFT, ClickButton.SHIFT_DOWN_RIGHT -> MyWhooshAction.SHIFT_DOWN
            ClickButton.Z -> if (config.rightPodOnlyNoZwift) MyWhooshAction.SHIFT_DOWN else MyWhooshAction.USE_POWERUP
            ClickButton.LEFT -> MyWhooshAction.STEER_LEFT
            ClickButton.RIGHT -> MyWhooshAction.STEER_RIGHT
            ClickButton.UP -> MyWhooshAction.TOGGLE_CAMERA
            ClickButton.DOWN -> MyWhooshAction.EMOTE
            ClickButton.A -> MyWhooshAction.UI_CONFIRM
            ClickButton.B -> MyWhooshAction.UI_BACK
            ClickButton.Y -> MyWhooshAction.HUD_TOGGLE
            else -> null
        }
    }
}

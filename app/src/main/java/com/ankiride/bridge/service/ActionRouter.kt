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

        // 1. User custom button overrides take top priority
        val customTarget = config.customBindings[ButtonBindingKey(event.button, event.type)]
        if (customTarget != null) {
            DiagnosticLogger.info(
                LogCategory.ROUTER,
                "Custom binding: ${event.button.name} (${event.type.name}) -> ${customTarget.label}"
            )
            customTarget.ankiAction?.let { ankiDispatcher.dispatch(it) }
            customTarget.myWhooshAction?.let { mw ->
                scope.launch { myWhooshDispatcher.dispatch(mw, config) }
            }
            return
        }

        // 2. Deterministic mode defaults
        when (config.mode) {
            OperatingMode.MODE_3_ANKI_ONLY -> {
                val ankiAction = mapAnkiOnlyMode(event) ?: return
                DiagnosticLogger.info(LogCategory.ROUTER, "Mode 3 (Anki-Only): ${event.button.name} -> $ankiAction")
                ankiDispatcher.dispatch(ankiAction)
            }
            OperatingMode.MODE_4_MYWHOOSH_ONLY -> {
                val mwAction = mapMyWhooshOnlyMode(event, config) ?: return
                DiagnosticLogger.info(LogCategory.ROUTER, "Mode 4 (MyWhoosh-Only): ${event.button.name} -> $mwAction")
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
        when {
            event.button.isShiftUpButton -> {
                val mwAction = if (event.type == PressType.LONG_PRESS) MyWhooshAction.SHIFT_DOWN else MyWhooshAction.SHIFT_UP
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: ${event.button.name} (${event.type.name}) -> $mwAction")
                scope.launch { myWhooshDispatcher.dispatch(mwAction, config) }
            }
            event.button.isShiftDownButton -> {
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: ${event.button.name} -> SHIFT_DOWN")
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
            }
            event.button == ClickButton.Z -> {
                if (event.type == PressType.LONG_PRESS) {
                    DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: Z (LONG_PRESS) -> UNDO")
                    ankiDispatcher.dispatch(AnkiAction.UNDO)
                } else if (config.useZForShiftDownInSinglePodMode) {
                    DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: Z (SINGLE_CLICK) -> SHIFT_DOWN")
                    scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
                } else {
                    DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: Z (SINGLE_CLICK) -> FLIP_OR_EASY")
                    ankiDispatcher.dispatch(AnkiAction.FLIP_OR_EASY)
                }
            }
            event.button == ClickButton.A -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.FLIP_OR_EASY else AnkiAction.FLIP_OR_GOOD
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: A (${event.type.name}) -> $action")
                ankiDispatcher.dispatch(action)
            }
            event.button == ClickButton.B -> {
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: B -> FLIP_OR_HARD")
                ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            }
            event.button == ClickButton.Y -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_AGAIN
                DiagnosticLogger.info(LogCategory.ROUTER, "1-Pod Combined: Y (${event.type.name}) -> $action")
                ankiDispatcher.dispatch(action)
            }
        }
    }

    private fun routeDualPodCombinedMode(event: ButtonGestureEvent, config: AppConfiguration) {
        when {
            event.button == ClickButton.A -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_GOOD)
            event.button == ClickButton.B -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_HARD)
            event.button == ClickButton.Y -> ankiDispatcher.dispatch(AnkiAction.FLIP_OR_AGAIN)
            event.button == ClickButton.Z -> {
                val action = if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_EASY
                ankiDispatcher.dispatch(action)
            }
            event.button.isShiftUpButton -> {
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_UP, config) }
            }
            event.button.isShiftDownButton -> {
                scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.SHIFT_DOWN, config) }
            }
            event.button == ClickButton.LEFT -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.STEER_LEFT, config) }
            event.button == ClickButton.RIGHT -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.STEER_RIGHT, config) }
            event.button == ClickButton.UP -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.TOGGLE_CAMERA, config) }
            event.button == ClickButton.DOWN -> scope.launch { myWhooshDispatcher.dispatch(MyWhooshAction.EMOTE, config) }
        }
    }

    private fun mapAnkiOnlyMode(event: ButtonGestureEvent): AnkiAction? {
        return when {
            event.button == ClickButton.A || event.button.isShiftUpButton -> AnkiAction.FLIP_OR_GOOD
            event.button == ClickButton.B -> AnkiAction.FLIP_OR_HARD
            event.button == ClickButton.Y -> if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_AGAIN
            event.button == ClickButton.Z -> if (event.type == PressType.LONG_PRESS) AnkiAction.UNDO else AnkiAction.FLIP_OR_EASY
            event.button.isShiftDownButton || event.button == ClickButton.LEFT -> AnkiAction.UNDO
            event.button == ClickButton.RIGHT || event.button == ClickButton.UP || event.button == ClickButton.DOWN -> AnkiAction.FLIP_CARD
            else -> null
        }
    }

    private fun mapMyWhooshOnlyMode(event: ButtonGestureEvent, config: AppConfiguration): MyWhooshAction? {
        return when {
            event.button.isShiftUpButton -> {
                if (config.rightPodOnlyNoZwift && event.type == PressType.LONG_PRESS) MyWhooshAction.SHIFT_DOWN
                else MyWhooshAction.SHIFT_UP
            }
            event.button.isShiftDownButton -> MyWhooshAction.SHIFT_DOWN
            event.button == ClickButton.Z -> if (config.rightPodOnlyNoZwift) MyWhooshAction.SHIFT_DOWN else MyWhooshAction.USE_POWERUP
            event.button == ClickButton.A -> if (config.rightPodOnlyNoZwift) MyWhooshAction.SHIFT_UP else MyWhooshAction.UI_CONFIRM
            event.button == ClickButton.B -> if (config.rightPodOnlyNoZwift) MyWhooshAction.SHIFT_DOWN else MyWhooshAction.UI_BACK
            event.button == ClickButton.Y -> MyWhooshAction.HUD_TOGGLE
            event.button == ClickButton.LEFT -> MyWhooshAction.STEER_LEFT
            event.button == ClickButton.RIGHT -> MyWhooshAction.STEER_RIGHT
            event.button == ClickButton.UP -> MyWhooshAction.TOGGLE_CAMERA
            event.button == ClickButton.DOWN -> MyWhooshAction.EMOTE
            else -> null
        }
    }
}

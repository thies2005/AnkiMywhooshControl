package com.ankiride.bridge.service

import android.content.Context
import com.ankiride.bridge.core.*
import com.ankiride.bridge.ui.FloatingHudPill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ActionRouter(
    private val context: Context? = null,
    private val scope: CoroutineScope,
    private val configSupplier: () -> AppConfiguration,
    private val ankiDispatcher: AnkiActionDispatcher,
    private val myWhooshDispatcher: MyWhooshActionDispatcher
) {

    fun routeGesture(event: ButtonGestureEvent) {
        val config = configSupplier()
        val target = ButtonMappingStore.resolveAction(
            mode = config.mode,
            button = event.button,
            pressType = event.type,
            customBindings = config.customBindings
        )

        DiagnosticLogger.info(
            LogCategory.ROUTER,
            "[${config.mode.shortTitle}] ${event.button.displayName} (${event.type.name}) -> ${target.label}"
        )

        if (context != null) {
            FeedbackCueManager.triggerCue(context, target, config)
        }

        FloatingHudPill.notifyActionDispatched(
            service = BridgeAccessibilityService.activeInstance,
            enabled = config.floatingHudPillEnabled,
            bleState = BridgeForegroundService.connectionStateFlow.value,
            mode = config.mode,
            actionText = target.label
        )

        target.ankiAction?.let { ankiAction ->
            ankiDispatcher.dispatch(ankiAction)
        }
        target.myWhooshAction?.let { mwAction ->
            scope.launch {
                myWhooshDispatcher.dispatch(mwAction, config)
            }
        }
    }
}

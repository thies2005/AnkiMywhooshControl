package com.ankiride.bridge.service

import com.ankiride.bridge.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DesktopActionRouter(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val configSupplier: () -> AppConfiguration,
    private val ankiDispatcher: AnkiActionDispatcher,
    private val myWhooshDispatcher: MyWhooshActionDispatcher
) {
    private val _lastRoutedActionLabel = MutableStateFlow("Ready")
    val lastRoutedActionLabel: StateFlow<String> = _lastRoutedActionLabel.asStateFlow()

    fun routeGesture(event: ButtonGestureEvent) {
        val config = configSupplier()
        val target = ButtonMappingStore.resolveAction(
            mode = config.mode,
            button = event.button,
            pressType = event.type,
            customBindings = config.customBindings
        )

        _lastRoutedActionLabel.value = target.label

        DiagnosticLogger.info(
            LogCategory.ROUTER,
            "[${config.mode.shortTitle}] ${event.button.displayName} (${event.type.name}) -> ${target.label}"
        )

        DesktopFeedbackCueManager.triggerCue(target, config)

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

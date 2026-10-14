package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher

class AnkiActionDispatcherImpl(
    private val bridge: AnkiAccessibilityBridge = AnkiAccessibilityBridge(),
    private val serviceProvider: () -> AccessibilityService?
) : AnkiActionDispatcher {

    override fun dispatch(action: AnkiAction): Boolean {
        val service = serviceProvider() ?: return false
        return bridge.performAnkiAction(service, action)
    }
}

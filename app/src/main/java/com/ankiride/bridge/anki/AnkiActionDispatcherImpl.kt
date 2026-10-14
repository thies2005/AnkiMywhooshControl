package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher

class AnkiActionDispatcherImpl(
    private val serviceProvider: () -> AccessibilityService?
) : AnkiActionDispatcher {

    override suspend fun dispatch(action: AnkiAction): Boolean {
        val service = serviceProvider() ?: return false
        return AnkiAccessibilityBridge.performAnkiAction(service, action)
    }
}

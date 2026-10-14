package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory

class AnkiAccessibilityBridge {

    companion object {
        const val ANKI_PACKAGE = "com.ichi2.anki"
        const val FLIP_TO_GRADE_GUARD_MS = 350L

        val SHOW_ANSWER_IDS = listOf(
            "com.ichi2.anki:id/show_answer",
            "com.ichi2.anki:id/flashcard_layout_flip",
            "com.ichi2.anki:id/answer_options_layout"
        )
        val EASE_1_IDS = listOf("com.ichi2.anki:id/again_button", "com.ichi2.anki:id/flashcard_layout_ease1")
        val EASE_2_IDS = listOf("com.ichi2.anki:id/hard_button", "com.ichi2.anki:id/flashcard_layout_ease2")
        val EASE_3_IDS = listOf("com.ichi2.anki:id/good_button", "com.ichi2.anki:id/flashcard_layout_ease3")
        val EASE_4_IDS = listOf("com.ichi2.anki:id/easy_button", "com.ichi2.anki:id/flashcard_layout_ease4")
        val UNDO_IDS = listOf("com.ichi2.anki:id/action_undo")

        fun dumpInteractiveWindowsToLog(service: AccessibilityService?) {
            if (service == null) {
                DiagnosticLogger.warn(LogCategory.ANKI, "Inspect Windows: BridgeAccessibilityService is NOT connected")
                return
            }
            val windows = service.windows
            if (windows.isNullOrEmpty()) {
                DiagnosticLogger.warn(LogCategory.ANKI, "Inspect Windows: service.windows is empty")
                return
            }
            DiagnosticLogger.info(LogCategory.ANKI, "=== Interactive Windows Dump (${windows.size} windows) ===")
            for ((idx, win) in windows.withIndex()) {
                val bounds = Rect()
                win.getBoundsInScreen(bounds)
                val pkg = win.root?.packageName?.toString() ?: "null"
                DiagnosticLogger.info(LogCategory.ANKI, "Window[$idx]: pkg=$pkg, bounds=$bounds, active=${win.isActive}, focused=${win.isFocused}")
            }
        }
    }

    @Volatile
    var lastFlipTimestampMs: Long = 0L

    fun performAnkiAction(service: AccessibilityService, action: AnkiAction, nowMs: Long = System.currentTimeMillis()): Boolean {
        val ankiWindow: AccessibilityWindowInfo? = service.windows
            ?.firstOrNull { it.root?.packageName?.toString() == ANKI_PACKAGE }

        val ankiRoot: AccessibilityNodeInfo? = ankiWindow?.root
            ?: service.rootInActiveWindow?.takeIf { it.packageName?.toString() == ANKI_PACKAGE }

        if (ankiRoot == null && ankiWindow == null) {
            DiagnosticLogger.warn(LogCategory.ANKI, "Cannot dispatch $action: com.ichi2.anki window not visible on screen")
            return false
        }

        if (ankiRoot != null) {
            val handledByNode = findAndClickButton(ankiRoot, action, nowMs)
            if (handledByNode) return true
        }

        // Layer 3 Fallback: Window-relative geometric tap on AnkiDroid's bottom answer bar
        if (ankiWindow != null && action != AnkiAction.UNDO) {
            if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) {
                DiagnosticLogger.warn(LogCategory.ANKI, "Bounce Guard (350ms) blocked Layer 3 tap for $action")
                return false
            }
            val bounds = Rect()
            ankiWindow.getBoundsInScreen(bounds)
            val tapped = dispatchBottomBarColumnTap(service, bounds, action)
            if (tapped) {
                DiagnosticLogger.info(LogCategory.ANKI, "Layer 3 (Geometric Tap) dispatched for $action in bounds=$bounds")
            } else {
                DiagnosticLogger.error(LogCategory.ANKI, "Layer 3 (Geometric Tap) failed for $action")
            }
            return tapped
        }
        DiagnosticLogger.warn(LogCategory.ANKI, "Action $action could not find matching AnkiDroid button")
        return false
    }

    fun findAndClickButton(
        root: AccessibilityNodeInfo?,
        action: AnkiAction,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (root == null || root.packageName?.toString() != ANKI_PACKAGE) return false
        if (root.isPassword) return false

        return when (action) {
            AnkiAction.FLIP_CARD -> {
                val flipped = clickByIdsOrText(root, SHOW_ANSWER_IDS, listOf("Show answer", "Show Answer"), action)
                if (flipped) lastFlipTimestampMs = nowMs
                flipped
            }
            AnkiAction.FLIP_OR_AGAIN -> clickShowAnswerOrEase(root, EASE_1_IDS, listOf("Again"), action, nowMs)
            AnkiAction.FLIP_OR_HARD -> clickShowAnswerOrEase(root, EASE_2_IDS, listOf("Hard", "Again"), action, nowMs)
            AnkiAction.FLIP_OR_GOOD -> clickShowAnswerOrEase(root, EASE_3_IDS, listOf("Good"), action, nowMs)
            AnkiAction.FLIP_OR_EASY -> clickShowAnswerOrEase(root, EASE_4_IDS, listOf("Easy", "Good"), action, nowMs)
            AnkiAction.UNDO -> clickByIdsOrText(root, UNDO_IDS, listOf("Undo"), action)
        }
    }

    private fun clickShowAnswerOrEase(
        root: AccessibilityNodeInfo,
        easeIds: List<String>,
        easeTexts: List<String>,
        action: AnkiAction,
        nowMs: Long
    ): Boolean {
        if (clickByIdsOrText(root, SHOW_ANSWER_IDS, listOf("Show answer", "Show Answer"), AnkiAction.FLIP_CARD)) {
            lastFlipTimestampMs = nowMs
            DiagnosticLogger.info(LogCategory.ANKI, "Flipped card via Show Answer (armed 350ms bounce guard)")
            return true
        }
        if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) {
            DiagnosticLogger.warn(LogCategory.ANKI, "Bounce Guard (350ms) blocked accidental double-press for $action")
            return false
        }
        return clickByIdsOrText(root, easeIds, easeTexts, action)
    }

    private fun clickByIdsOrText(
        root: AccessibilityNodeInfo,
        viewIds: List<String>,
        fallbackTexts: List<String>,
        action: AnkiAction
    ): Boolean {
        for (id in viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            for (node in nodes) {
                if (!node.isPassword && node.isVisibleToUser && clickNodeOrParent(node)) {
                    DiagnosticLogger.info(LogCategory.ANKI, "Layer 1 (ViewId '$id') clicked for $action")
                    return true
                }
            }
        }
        for (text in fallbackTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text) ?: continue
            for (node in nodes) {
                if (!node.isPassword && node.isVisibleToUser && clickNodeOrParent(node)) {
                    DiagnosticLogger.info(LogCategory.ANKI, "Layer 2 (Text '$text') clicked for $action")
                    return true
                }
            }
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isPassword) return false
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun dispatchBottomBarColumnTap(
        service: AccessibilityService,
        windowBounds: Rect,
        action: AnkiAction
    ): Boolean {
        if (windowBounds.width() <= 0 || windowBounds.height() <= 0) return false
        val colNormX = when (action) {
            AnkiAction.FLIP_OR_AGAIN -> 0.125f
            AnkiAction.FLIP_OR_HARD -> 0.375f
            AnkiAction.FLIP_OR_GOOD, AnkiAction.FLIP_CARD -> 0.625f
            AnkiAction.FLIP_OR_EASY -> 0.875f
            AnkiAction.UNDO -> return false
        }
        val tapX = windowBounds.left + (windowBounds.width() * colNormX)
        val tapY = windowBounds.top + (windowBounds.height() * 0.93f)

        val path = Path().apply { moveTo(tapX, tapY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 15L)
        return service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}

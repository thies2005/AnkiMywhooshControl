package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ankiride.bridge.core.AnkiAction

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
    }

    @Volatile
    var lastFlipTimestampMs: Long = 0L

    fun performAnkiAction(service: AccessibilityService, action: AnkiAction, nowMs: Long = System.currentTimeMillis()): Boolean {
        val ankiWindow: AccessibilityWindowInfo? = service.windows
            ?.firstOrNull { it.root?.packageName?.toString() == ANKI_PACKAGE }

        val ankiRoot: AccessibilityNodeInfo? = ankiWindow?.root
            ?: service.rootInActiveWindow?.takeIf { it.packageName?.toString() == ANKI_PACKAGE }

        if (ankiRoot != null) {
            val handledByNode = findAndClickButton(ankiRoot, action, nowMs)
            if (handledByNode) return true
        }

        // Layer 3 Fallback: Window-relative geometric tap on AnkiDroid's bottom answer bar
        if (ankiWindow != null && action != AnkiAction.UNDO) {
            if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) return false
            val bounds = Rect()
            ankiWindow.getBoundsInScreen(bounds)
            return dispatchBottomBarColumnTap(service, bounds, action)
        }
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
                val flipped = clickByIdsOrText(root, SHOW_ANSWER_IDS, listOf("Show answer", "Show Answer"))
                if (flipped) lastFlipTimestampMs = nowMs
                flipped
            }
            AnkiAction.FLIP_OR_AGAIN -> clickShowAnswerOrEase(root, EASE_1_IDS, listOf("Again"), nowMs)
            AnkiAction.FLIP_OR_HARD -> clickShowAnswerOrEase(root, EASE_2_IDS, listOf("Hard", "Again"), nowMs)
            AnkiAction.FLIP_OR_GOOD -> clickShowAnswerOrEase(root, EASE_3_IDS, listOf("Good"), nowMs)
            AnkiAction.FLIP_OR_EASY -> clickShowAnswerOrEase(root, EASE_4_IDS, listOf("Easy", "Good"), nowMs)
            AnkiAction.UNDO -> clickByIdsOrText(root, UNDO_IDS, listOf("Undo"))
        }
    }

    private fun clickShowAnswerOrEase(
        root: AccessibilityNodeInfo,
        easeIds: List<String>,
        easeTexts: List<String>,
        nowMs: Long
    ): Boolean {
        // Step 1: If "Show Answer" is visible, flip the card and arm the 350ms bounce guard
        if (clickByIdsOrText(root, SHOW_ANSWER_IDS, listOf("Show answer", "Show Answer"))) {
            lastFlipTimestampMs = nowMs
            return true
        }
        // Step 2: Enforce 350ms post-flip guard so a double-bounce never grades the card unread
        if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) {
            return false
        }
        // Step 3: Click the target Ease button (Again / Hard / Good / Easy)
        return clickByIdsOrText(root, easeIds, easeTexts)
    }

    private fun clickByIdsOrText(
        root: AccessibilityNodeInfo,
        viewIds: List<String>,
        fallbackTexts: List<String>
    ): Boolean {
        for (id in viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            for (node in nodes) {
                if (!node.isPassword && node.isVisibleToUser && clickNodeOrParent(node)) {
                    return true
                }
            }
        }
        for (text in fallbackTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text) ?: continue
            for (node in nodes) {
                if (!node.isPassword && node.isVisibleToUser && clickNodeOrParent(node)) {
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

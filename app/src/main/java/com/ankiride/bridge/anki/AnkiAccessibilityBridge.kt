package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.ankiride.bridge.core.AnkiAction

object AnkiAccessibilityBridge {
    const val ANKI_PACKAGE = "com.ichi2.anki"

    val SHOW_ANSWER_IDS = listOf(
        "com.ichi2.anki:id/show_answer",
        "com.ichi2.anki:id/flashcard_layout_flip"
    )

    val EASE_1_IDS = listOf("com.ichi2.anki:id/again_button", "com.ichi2.anki:id/flashcard_layout_ease1")
    val EASE_2_IDS = listOf("com.ichi2.anki:id/hard_button", "com.ichi2.anki:id/flashcard_layout_ease2")
    val EASE_3_IDS = listOf("com.ichi2.anki:id/good_button", "com.ichi2.anki:id/flashcard_layout_ease3")
    val EASE_4_IDS = listOf("com.ichi2.anki:id/easy_button", "com.ichi2.anki:id/flashcard_layout_ease4")
    val UNDO_IDS = listOf("com.ichi2.anki:id/action_undo")

    fun performAnkiAction(service: AccessibilityService, action: AnkiAction): Boolean {
        val ankiRoot = service.windows
            ?.firstOrNull { it.root?.packageName?.toString() == ANKI_PACKAGE }
            ?.root ?: return false

        return performAnkiActionOnRoot(ankiRoot, action)
    }

    fun performAnkiActionOnRoot(ankiRoot: AccessibilityNodeInfo, action: AnkiAction): Boolean {
        // Security Gate SG-2 check: verify package and reject password fields
        if (ankiRoot.packageName?.toString() != ANKI_PACKAGE) return false
        if (ankiRoot.isPassword) return false

        return when (action) {
            AnkiAction.FLIP_OR_EASE_1_AGAIN -> clickShowAnswerOrEase(ankiRoot, EASE_1_IDS, "Again")
            AnkiAction.FLIP_OR_EASE_2_HARD -> clickShowAnswerOrEase(ankiRoot, EASE_2_IDS, "Hard")
            AnkiAction.FLIP_OR_EASE_3_GOOD -> clickShowAnswerOrEase(ankiRoot, EASE_3_IDS, "Good")
            AnkiAction.FLIP_OR_EASE_4_EASY -> clickShowAnswerOrEase(ankiRoot, EASE_4_IDS, "Easy")
            AnkiAction.UNDO -> clickNodeByIdsOrText(ankiRoot, UNDO_IDS, "Undo")
            AnkiAction.BURY_CARD -> clickNodeByIdsOrText(ankiRoot, emptyList(), "Bury")
            AnkiAction.REPLAY_AUDIO -> clickNodeByIdsOrText(ankiRoot, emptyList(), "Replay audio")
            AnkiAction.PREV_HINT, AnkiAction.NEXT_HINT -> clickNodeByIdsOrText(ankiRoot, emptyList(), "Hint")
        }
    }

    private fun clickShowAnswerOrEase(
        root: AccessibilityNodeInfo,
        easeIds: List<String>,
        easeFallbackText: String
    ): Boolean {
        if (clickNodeByIdsOrText(root, SHOW_ANSWER_IDS, "Show answer")) {
            return true
        }
        return clickNodeByIdsOrText(root, easeIds, easeFallbackText)
    }

    private fun clickNodeByIdsOrText(
        root: AccessibilityNodeInfo,
        viewIds: List<String>,
        fallbackText: String
    ): Boolean {
        for (id in viewIds) {
            val matches = root.findAccessibilityNodeInfosByViewId(id)
            val clickable = matches?.firstOrNull { !it.isPassword && it.isVisibleToUser }
            if (clickable != null && performClickUpHierarchy(clickable)) {
                return true
            }
        }
        val textMatches = root.findAccessibilityNodeInfosByText(fallbackText)
        val clickableByText = textMatches?.firstOrNull { !it.isPassword && it.isVisibleToUser }
        return clickableByText != null && performClickUpHierarchy(clickableByText)
    }

    private fun performClickUpHierarchy(node: AccessibilityNodeInfo): Boolean {
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
}

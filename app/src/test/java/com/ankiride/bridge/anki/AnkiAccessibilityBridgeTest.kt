package com.ankiride.bridge.anki

import com.ankiride.bridge.core.AnkiAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiAccessibilityBridgeTest {

    @Test
    fun testPackageConstants() {
        assertEquals("com.ichi2.anki", AnkiAccessibilityBridge.ANKI_PACKAGE)
        assertTrue(AnkiAccessibilityBridge.SHOW_ANSWER_IDS.contains("com.ichi2.anki:id/show_answer"))
        assertTrue(AnkiAccessibilityBridge.SHOW_ANSWER_IDS.contains("com.ichi2.anki:id/flashcard_layout_flip"))
        assertTrue(AnkiAccessibilityBridge.EASE_1_IDS.contains("com.ichi2.anki:id/again_button"))
        assertTrue(AnkiAccessibilityBridge.EASE_2_IDS.contains("com.ichi2.anki:id/hard_button"))
        assertTrue(AnkiAccessibilityBridge.EASE_3_IDS.contains("com.ichi2.anki:id/good_button"))
        assertTrue(AnkiAccessibilityBridge.EASE_4_IDS.contains("com.ichi2.anki:id/easy_button"))
        assertTrue(AnkiAccessibilityBridge.UNDO_IDS.contains("com.ichi2.anki:id/action_undo"))
    }

    @Test
    fun testContentProviderUriAndColumns() {
        assertEquals(
            "content://com.ichi2.anki.flashcards/schedule",
            AnkiContentProviderHelper.REVIEW_INFO_URI_STRING
        )
        assertEquals("deck_id", AnkiContentProviderHelper.COLUMN_DECK_ID)
        assertEquals("deck_name", AnkiContentProviderHelper.COLUMN_DECK_NAME)
        assertEquals("new_count", AnkiContentProviderHelper.COLUMN_NEW_COUNT)
        assertEquals("lrn_count", AnkiContentProviderHelper.COLUMN_LRN_COUNT)
        assertEquals("rev_count", AnkiContentProviderHelper.COLUMN_REV_COUNT)
    }

    @Test
    fun testDispatcherReturnsFalseWhenServiceNull() = runBlocking {
        val dispatcher = AnkiActionDispatcherImpl(serviceProvider = { null })
        val result = dispatcher.dispatch(AnkiAction.FLIP_OR_EASE_3_GOOD)
        assertFalse(result)
    }
}

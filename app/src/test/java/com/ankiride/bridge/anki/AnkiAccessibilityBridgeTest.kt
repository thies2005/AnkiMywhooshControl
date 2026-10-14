package com.ankiride.bridge.anki

import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnkiAccessibilityBridgeTest {

    @Before
    fun setUp() {
        DiagnosticLogger.clear()
    }

    @Test
    fun testPackageConstants() {
        assertEquals("com.ichi2.anki", AnkiAccessibilityBridge.ANKI_PACKAGE)
        assertEquals(350L, AnkiAccessibilityBridge.FLIP_TO_GRADE_GUARD_MS)
        assertTrue(AnkiAccessibilityBridge.SHOW_ANSWER_IDS.contains("com.ichi2.anki:id/show_answer"))
        assertTrue(AnkiAccessibilityBridge.SHOW_ANSWER_IDS.contains("com.ichi2.anki:id/flashcard_layout_flip"))
        assertTrue(AnkiAccessibilityBridge.SHOW_ANSWER_IDS.contains("com.ichi2.anki:id/answer_options_layout"))
        assertTrue(AnkiAccessibilityBridge.EASE_1_IDS.contains("com.ichi2.anki:id/again_button"))
        assertTrue(AnkiAccessibilityBridge.EASE_2_IDS.contains("com.ichi2.anki:id/hard_button"))
        assertTrue(AnkiAccessibilityBridge.EASE_3_IDS.contains("com.ichi2.anki:id/good_button"))
        assertTrue(AnkiAccessibilityBridge.EASE_4_IDS.contains("com.ichi2.anki:id/easy_button"))
        assertTrue(AnkiAccessibilityBridge.UNDO_IDS.contains("com.ichi2.anki:id/action_undo"))
    }

    @Test
    fun testDumpInteractiveWindowsToLogWhenServiceNull() {
        AnkiAccessibilityBridge.dumpInteractiveWindowsToLog(null)
        val logs = DiagnosticLogger.logs.value
        assertEquals(1, logs.size)
        assertEquals(LogCategory.ANKI, logs[0].category)
        assertEquals(LogLevel.WARN, logs[0].level)
        assertTrue(logs[0].message.contains("BridgeAccessibilityService is NOT connected"))
    }

    @Test
    fun testFindAndClickButtonWithNullRoot() {
        val bridge = AnkiAccessibilityBridge()
        assertEquals(0L, bridge.lastFlipTimestampMs)
        val result = bridge.findAndClickButton(null, AnkiAction.FLIP_CARD)
        assertFalse(result)
        assertEquals(0L, bridge.lastFlipTimestampMs)
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
    fun testAnkiDeckReviewInfoModel() {
        val info = AnkiDeckReviewInfo(
            deckId = 42L,
            deckName = "German",
            newCount = 5,
            learnCount = 10,
            reviewCount = 15,
            totalDue = 30
        )
        assertEquals(42L, info.deckId)
        assertEquals("German", info.deckName)
        assertEquals(5, info.newCount)
        assertEquals(10, info.learnCount)
        assertEquals(15, info.reviewCount)
        assertEquals(30, info.totalDue)
    }

    @Test
    fun testDispatcherReturnsFalseWhenServiceNull() {
        val dispatcher = AnkiActionDispatcherImpl(serviceProvider = { null })
        val result = dispatcher.dispatch(AnkiAction.FLIP_OR_GOOD)
        assertFalse(result)
    }
}

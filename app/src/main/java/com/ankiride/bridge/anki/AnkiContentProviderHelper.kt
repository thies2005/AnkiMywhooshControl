package com.ankiride.bridge.anki

import android.content.Context
import android.net.Uri
import android.util.Log

data class AnkiDeckReviewInfo(
    val deckId: Long = 0L,
    val deckName: String = "",
    val newCount: Int = 0,
    val learnCount: Int = 0,
    val reviewCount: Int = 0,
    val totalDue: Int = 0
)

object AnkiContentProviderHelper {
    private const val TAG = "AnkiContentProvider"
    const val REVIEW_INFO_URI_STRING = "content://com.ichi2.anki.flashcards/schedule"
    val REVIEW_INFO_CONTENT_URI: Uri by lazy { Uri.parse(REVIEW_INFO_URI_STRING) }

    const val COLUMN_DECK_ID = "deck_id"
    const val COLUMN_DECK_NAME = "deck_name"
    const val COLUMN_NEW_COUNT = "new_count"
    const val COLUMN_LRN_COUNT = "lrn_count"
    const val COLUMN_REV_COUNT = "rev_count"

    /**
     * Queries AnkiDroid ContentProvider for deck review statistics safely.
     * Uses parameterized selectionArgs to prevent SQL injection.
     */
    fun queryReviewInfo(context: Context, targetDeckName: String? = null): List<AnkiDeckReviewInfo> {
        val results = mutableListOf<AnkiDeckReviewInfo>()
        val projection = arrayOf(
            COLUMN_DECK_ID,
            COLUMN_DECK_NAME,
            COLUMN_NEW_COUNT,
            COLUMN_LRN_COUNT,
            COLUMN_REV_COUNT
        )

        val (selection, selectionArgs) = if (targetDeckName != null) {
            "$COLUMN_DECK_NAME = ?" to arrayOf(targetDeckName)
        } else {
            null to null
        }

        try {
            val cursor = context.contentResolver.query(
                REVIEW_INFO_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            cursor?.use {
                val deckIdCol = it.getColumnIndex(COLUMN_DECK_ID)
                val deckNameCol = it.getColumnIndex(COLUMN_DECK_NAME)
                val newCol = it.getColumnIndex(COLUMN_NEW_COUNT)
                val lrnCol = it.getColumnIndex(COLUMN_LRN_COUNT)
                val revCol = it.getColumnIndex(COLUMN_REV_COUNT)

                while (it.moveToNext()) {
                    val deckId = if (deckIdCol >= 0) it.getLong(deckIdCol) else 0L
                    val name = if (deckNameCol >= 0) it.getString(deckNameCol) ?: "" else ""
                    val newCount = if (newCol >= 0) it.getInt(newCol) else 0
                    val lrnCount = if (lrnCol >= 0) it.getInt(lrnCol) else 0
                    val revCount = if (revCol >= 0) it.getInt(revCol) else 0
                    val totalDue = newCount + lrnCount + revCount

                    results.add(
                        AnkiDeckReviewInfo(
                            deckId = deckId,
                            deckName = name,
                            newCount = newCount,
                            learnCount = lrnCount,
                            reviewCount = revCount,
                            totalDue = totalDue
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to query AnkiDroid ContentProvider", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying AnkiDroid ContentProvider", e)
        }
        return results
    }
}

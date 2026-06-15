package com.jpillion.dailyreadingplanner.bible.ui.picker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VC-T4 — two-step picker behavior under Robolectric (the project's Compose-UI harness).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookChapterPickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `step 1 lists both testaments — Genesis under OT and Matthew under NT`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                BookChapterPicker(onChapterSelected = { _, _ -> })
            }
        }
        composeRule.onNodeWithTag("picker-testament-ot").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-book-list").performScrollToNode(hasTestTag("picker-book-1"))
        composeRule.onNodeWithTag("picker-book-1").assertExists() // Genesis (OT)
        composeRule.onNodeWithTag("picker-book-list").performScrollToNode(hasTestTag("picker-testament-nt"))
        composeRule.onNodeWithTag("picker-testament-nt").assertExists()
        composeRule.onNodeWithTag("picker-book-list").performScrollToNode(hasTestTag("picker-book-40"))
        composeRule.onNodeWithTag("picker-book-40").assertExists() // Matthew (NT)
    }

    @Test
    fun `selecting a book opens its chapter grid sized to chapterCount`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                BookChapterPicker(onChapterSelected = { _, _ -> })
            }
        }
        // Genesis (order 1) has 50 chapters.
        composeRule.onNodeWithTag("picker-book-1").performClick()
        composeRule.onNodeWithTag("picker-chapter-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-chapter-1").assertExists()
        composeRule.onNodeWithTag("picker-chapter-51").assertDoesNotExist() // Genesis has 50
    }

    @Test
    fun `selecting a chapter reports the book and chapter`() {
        var picked: Pair<Book, Int>? = null
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                BookChapterPicker(onChapterSelected = { b, c -> picked = b to c })
            }
        }
        composeRule.onNodeWithTag("picker-book-1").performClick()
        composeRule.onNodeWithTag("picker-chapter-2").performClick()
        assertThat(picked?.first?.canonicalName).isEqualTo("Genesis")
        assertThat(picked?.second).isEqualTo(2)
    }

    @Test
    fun `back from the grid returns to the book list`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                BookChapterPicker(onChapterSelected = { _, _ -> })
            }
        }
        composeRule.onNodeWithTag("picker-book-1").performClick()
        composeRule.onNodeWithTag("picker-chapter-back").performClick()
        composeRule.onNodeWithTag("picker-book-list").assertIsDisplayed()
    }
}

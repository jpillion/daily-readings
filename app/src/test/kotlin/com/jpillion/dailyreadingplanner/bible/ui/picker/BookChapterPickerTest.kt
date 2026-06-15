package com.jpillion.dailyreadingplanner.bible.ui.picker

import androidx.compose.ui.test.assertContentDescriptionEquals
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
 * VC-T4 — two-step book → chapter picker behavior under Robolectric. Step 1 is now a dense GRID of
 * abbreviated book names (owner redesign) with OT/NT section headers; step 2 is the chapter grid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookChapterPickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `step 1 is a grid showing both testaments with Genesis under OT and Matthew under NT`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                BookChapterPicker(onChapterSelected = { _, _ -> })
            }
        }
        composeRule.onNodeWithTag("picker-book-list").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-testament-ot").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-book-1").assertExists() // Genesis (OT)
        composeRule.onNodeWithTag("picker-book-list").performScrollToNode(hasTestTag("picker-testament-nt"))
        composeRule.onNodeWithTag("picker-testament-nt").assertExists()
        composeRule.onNodeWithTag("picker-book-list").performScrollToNode(hasTestTag("picker-book-40"))
        composeRule.onNodeWithTag("picker-book-40").assertExists() // Matthew (NT)
    }

    @Test
    fun `book cells show the abbreviated label but speak the full canonical name`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                BookChapterPicker(onChapterSelected = { _, _ -> })
            }
        }
        // The cell speaks the full canonical name even though its on-glass label is the
        // catalog abbreviation (displayAbbrev "Gen", whose own semantics are cleared so the
        // cell speaks "Genesis" once, not "Gen Genesis"). On-glass abbrev text = device-pass.
        composeRule.onNodeWithTag("picker-book-1").assertContentDescriptionEquals("Genesis")
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
    fun `chapter cells show the number but speak the book and chapter`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                BookChapterPicker(onChapterSelected = { _, _ -> })
            }
        }
        composeRule.onNodeWithTag("picker-book-1").performClick()
        composeRule.onNodeWithTag("picker-chapter-3").assertContentDescriptionEquals("Genesis chapter 3")
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
    fun `back from the grid returns to the book grid`() {
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

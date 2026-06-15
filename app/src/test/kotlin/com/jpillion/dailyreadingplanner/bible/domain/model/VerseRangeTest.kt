package com.jpillion.dailyreadingplanner.bible.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test

class VerseRangeTest {
    @Test
    fun `rejects reversed range`() {
        assertThrows(IllegalArgumentException::class.java) {
            VerseRange(43_003_016L, 43_003_010L)
        }
    }

    @Test
    fun `accepts single-verse range`() {
        VerseRange(43_003_016L, 43_003_016L) // start == end is valid
    }
}

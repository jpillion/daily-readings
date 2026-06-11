package com.jpillion.dailyreadingplanner.ui.today

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.portion
import org.junit.Test

class ReadingFormatterTest {
    @Test
    fun `single chapter formats without a range`() {
        val p = portion(Stream.LAW_AND_HISTORY, "Genesis" to 1)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Genesis 1")
    }

    @Test
    fun `consecutive chapters collapse to an en-dash range`() {
        val p = portion(Stream.LAW_AND_HISTORY, "Genesis" to 1, "Genesis" to 2)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Genesis 1–2")
    }

    @Test
    fun `three consecutive chapters collapse to one range`() {
        val p = portion(Stream.LAW_AND_HISTORY, "Genesis" to 5, "Genesis" to 6, "Genesis" to 7)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Genesis 5–7")
    }

    @Test
    fun `multi-book portion joins runs with semicolons - Jun 19 case`() {
        val p = portion(Stream.NEW_TESTAMENT, "2 John" to 1, "3 John" to 1)
        assertThat(ReadingFormatter.format(p)).isEqualTo("2 John 1; 3 John 1")
    }

    @Test
    fun `non-consecutive chapters of the same book do not collapse`() {
        val p = portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 5, "Psalms" to 7)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Psalms 5; Psalms 7")
    }

    @Test
    fun `book boundary breaks a run even when chapter numbers are consecutive`() {
        val p = portion(Stream.LAW_AND_HISTORY, "Genesis" to 50, "Exodus" to 1)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Genesis 50; Exodus 1")
    }

    @Test
    fun `stream titles match the Bible Companion stream names`() {
        assertThat(ReadingFormatter.streamTitle(Stream.LAW_AND_HISTORY)).isEqualTo("Law & History")
        assertThat(ReadingFormatter.streamTitle(Stream.PSALMS_AND_PROPHECY)).isEqualTo("Psalms & Prophecy")
        assertThat(ReadingFormatter.streamTitle(Stream.NEW_TESTAMENT)).isEqualTo("New Testament")
    }
}

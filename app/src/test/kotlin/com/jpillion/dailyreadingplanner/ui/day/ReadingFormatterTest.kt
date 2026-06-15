package com.jpillion.dailyreadingplanner.ui.day

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.portion
import com.jpillion.dailyreadingplanner.domain.windowedPortion
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
        assertThat(ReadingFormatter.format(p)).isEqualTo("Psalm 5; Psalm 7")
    }

    @Test
    fun `book boundary breaks a run even when chapter numbers are consecutive`() {
        val p = portion(Stream.LAW_AND_HISTORY, "Genesis" to 50, "Exodus" to 1)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Genesis 50; Exodus 1")
    }

    // --- Psalms singular/plural (D-UI-2, owner request) --------------------------------

    @Test
    fun `a single chapter of Psalms renders the singular Psalm`() {
        val p = portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 23)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Psalm 23")
    }

    @Test
    fun `a single Psalms chapter with a verse window renders the singular Psalm`() {
        val p = windowedPortion(Stream.PSALMS_AND_PROPHECY, "Psalms", 119, 1, 40)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Psalm 119:1–40")
    }

    @Test
    fun `multiple chapters of Psalms keep the plural Psalms`() {
        val p = portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 1, "Psalms" to 2)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Psalms 1–2")
        val high = portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 149, "Psalms" to 150)
        assertThat(ReadingFormatter.format(high)).isEqualTo("Psalms 149–150")
    }

    @Test
    fun `the singular Psalm only applies to a single chapter not a range - mutation guard`() {
        // Forcing always-plural turns the single-chapter assertion red; forcing always-singular
        // turns the multi-chapter assertion red. Both directions are pinned here.
        assertThat(ReadingFormatter.format(portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 23)))
            .isEqualTo("Psalm 23")
        assertThat(ReadingFormatter.format(portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 1, "Psalms" to 2)))
            .isEqualTo("Psalms 1–2")
    }

    @Test
    fun `the singular rule is Psalms-specific - other single-chapter books are unchanged`() {
        assertThat(ReadingFormatter.format(portion(Stream.NEW_TESTAMENT, "Obadiah" to 1)))
            .isEqualTo("Obadiah 1")
        assertThat(ReadingFormatter.format(portion(Stream.NEW_TESTAMENT, "Philemon" to 1)))
            .isEqualTo("Philemon 1")
    }

    @Test
    fun `the abbreviated form keeps Psa for both single and multiple chapters - D-UI-2`() {
        // "Psa" reads fine singular or plural, so the abbreviation is left unchanged either way.
        assertThat(ReadingFormatter.formatAbbreviated(portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 23)))
            .isEqualTo("Psa 23")
        assertThat(
            ReadingFormatter.formatAbbreviated(windowedPortion(Stream.PSALMS_AND_PROPHECY, "Psalms", 119, 1, 40)),
        ).isEqualTo("Psa 119:1–40")
        assertThat(
            ReadingFormatter.formatAbbreviated(portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 1, "Psalms" to 2)),
        ).isEqualTo("Psa 1–2")
    }

    // -----------------------------------------------------------------------------------

    @Test
    fun `abbreviated form uses derived display abbrevs with the same range collapse - D-S9-1`() {
        val p = portion(Stream.LAW_AND_HISTORY, "Genesis" to 1, "Genesis" to 2)
        assertThat(ReadingFormatter.formatAbbreviated(p)).isEqualTo("Gen 1–2")
        val psalms = portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 1, "Psalms" to 2)
        assertThat(ReadingFormatter.formatAbbreviated(psalms)).isEqualTo("Psa 1–2")
        val matthew = portion(Stream.NEW_TESTAMENT, "Matthew" to 1, "Matthew" to 2)
        assertThat(ReadingFormatter.formatAbbreviated(matthew)).isEqualTo("Mat 1–2")
    }

    @Test
    fun `abbreviated two-book portion still joins runs - Jun 19 and Dec 19 case`() {
        val p = portion(Stream.NEW_TESTAMENT, "2 John" to 1, "3 John" to 1)
        assertThat(ReadingFormatter.formatAbbreviated(p)).isEqualTo("2Jo 1; 3Jo 1")
    }

    @Test
    fun `abbreviated single chapter formats without a range`() {
        val p = portion(Stream.PSALMS_AND_PROPHECY, "Obadiah" to 1)
        assertThat(ReadingFormatter.formatAbbreviated(p)).isEqualTo("Oba 1")
    }

    @Test
    fun `stream titles match the Bible Companion stream names`() {
        assertThat(ReadingFormatter.streamTitle(Stream.LAW_AND_HISTORY)).isEqualTo("Law & History")
        assertThat(ReadingFormatter.streamTitle(Stream.PSALMS_AND_PROPHECY)).isEqualTo("Psalms & Prophecy")
        assertThat(ReadingFormatter.streamTitle(Stream.NEW_TESTAMENT)).isEqualTo("New Testament")
    }

    @Test
    fun `a verse-windowed ref renders book chapter and verse range with an en dash - D-UI-1`() {
        val p = windowedPortion(Stream.PSALMS_AND_PROPHECY, "Psalms", 119, 1, 40)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Psalm 119:1–40")
    }

    @Test
    fun `all four Psalm 119 days render their windows`() {
        assertThat(ReadingFormatter.format(windowedPortion(Stream.PSALMS_AND_PROPHECY, "Psalms", 119, 41, 80)))
            .isEqualTo("Psalm 119:41–80")
        assertThat(ReadingFormatter.format(windowedPortion(Stream.PSALMS_AND_PROPHECY, "Psalms", 119, 81, 128)))
            .isEqualTo("Psalm 119:81–128")
        assertThat(ReadingFormatter.format(windowedPortion(Stream.PSALMS_AND_PROPHECY, "Psalms", 119, 129, 176)))
            .isEqualTo("Psalm 119:129–176")
    }

    @Test
    fun `a single-verse window renders without a range`() {
        val p = windowedPortion(Stream.PSALMS_AND_PROPHECY, "Psalms", 119, 7, 7)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Psalm 119:7")
    }

    @Test
    fun `the abbreviated form also renders the verse window - widget and notification surfaces`() {
        val p = windowedPortion(Stream.PSALMS_AND_PROPHECY, "Psalms", 119, 1, 40)
        assertThat(ReadingFormatter.formatAbbreviated(p)).isEqualTo("Psa 119:1–40")
    }

    @Test
    fun `a windowed ref never merges into a consecutive-chapter run - it is its own run`() {
        // Psalms 118 (whole) then Psalms 119:1-40 (windowed): the window must NOT merge with 118.
        // 118 is a single-chapter run -> "Psalm 118"; the window is its own single-chapter run -> "Psalm".
        val refs =
            listOf(
                com.jpillion.dailyreadingplanner.domain.model.Reference(
                    com.jpillion.dailyreadingplanner.data.reference.BookCatalog
                        .requireByName("Psalms"),
                    118,
                ),
                com.jpillion.dailyreadingplanner.domain.model.Reference(
                    com.jpillion.dailyreadingplanner.data.reference.BookCatalog
                        .requireByName("Psalms"),
                    119,
                    com.jpillion.dailyreadingplanner.domain.model
                        .ReferenceVerses(1, 40),
                ),
            )
        val p =
            com.jpillion.dailyreadingplanner.domain.model
                .Portion(Stream.PSALMS_AND_PROPHECY, refs)
        assertThat(ReadingFormatter.format(p)).isEqualTo("Psalm 118; Psalm 119:1–40")
    }
}

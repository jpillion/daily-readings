package com.jpillion.dailyreadingplanner.bible.data.markup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** VA-T5 — pins the strip contract per tag (D-V3-6). Mutation target: dropping inner text or
 *  keeping a tag must break a pin. */
class MarkupStripperTest {
    @Test
    fun `added tag - drops tag keeps inner text`() {
        assertThat(MarkupStripper.strip("The LORD <a>is</a> my shepherd"))
            .isEqualTo("The LORD is my shepherd")
    }

    @Test
    fun `words-of-christ tag - drops tag keeps inner text`() {
        assertThat(MarkupStripper.strip("He said, <w>Follow me</w> today"))
            .isEqualTo("He said, Follow me today")
    }

    @Test
    fun `line break self-closing collapses to a single space`() {
        assertThat(MarkupStripper.strip("first line<l/>second line"))
            .isEqualTo("first line second line")
        assertThat(MarkupStripper.strip("first line<l />second line"))
            .isEqualTo("first line second line")
    }

    @Test
    fun `whitespace normalized and trimmed`() {
        assertThat(MarkupStripper.strip("  In   the    beginning  "))
            .isEqualTo("In the beginning")
    }

    @Test
    fun `plain text unchanged`() {
        assertThat(MarkupStripper.strip("Jesus wept.")).isEqualTo("Jesus wept.")
    }

    @Test
    fun `nested and multiple added tags`() {
        assertThat(MarkupStripper.strip("a <a>b</a> c <a>d e</a> f"))
            .isEqualTo("a b c d e f")
    }

    @Test
    fun `total - empty and tag-only inputs do not throw`() {
        assertThat(MarkupStripper.strip("")).isEqualTo("")
        assertThat(MarkupStripper.strip("<a></a>")).isEqualTo("")
    }
}

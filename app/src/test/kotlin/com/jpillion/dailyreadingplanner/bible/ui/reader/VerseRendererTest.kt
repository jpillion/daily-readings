package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.ui.text.font.FontStyle
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.data.markup.MarkupStripper
import org.junit.Test

/**
 * VC-T2 — pure render pins for [VerseRenderer]. No Compose runtime: the renderer only builds an
 * AnnotatedString, so spans are asserted directly. The italic added-word span (test 2) is the
 * load-bearing render-logic mutation target.
 */
class VerseRendererTest {
    @Test
    fun `plain text renders verbatim with no spans`() {
        val out = VerseRenderer.render("In the beginning God created")
        assertThat(out.text).isEqualTo("In the beginning God created")
        assertThat(out.spanStyles).isEmpty()
    }

    @Test
    fun `added word renders inner text in exactly one italic span over that word`() {
        val out = VerseRenderer.render("In the <a>beginning</a> God")
        assertThat(out.text).isEqualTo("In the beginning God")
        val italics = out.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertThat(italics).hasSize(1)
        val span = italics.single()
        assertThat(out.text.substring(span.start, span.end)).isEqualTo("beginning")
    }

    @Test
    fun `multiple added words each get their own italic span and plain text between is unstyled`() {
        val out = VerseRenderer.render("a <a>b</a> c <a>d</a> e")
        assertThat(out.text).isEqualTo("a b c d e")
        val italics = out.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertThat(italics).hasSize(2)
        assertThat(italics.map { out.text.substring(it.start, it.end) }).containsExactly("b", "d")
    }

    @Test
    fun `words of Christ keeps inner text and applies no italic span in V3`() {
        val out = VerseRenderer.render("<w>Jesus said</w> unto them")
        assertThat(out.text).isEqualTo("Jesus said unto them")
        assertThat(out.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }).isEmpty()
    }

    @Test
    fun `line break renders a newline`() {
        val out = VerseRenderer.render("first line<l/>second line")
        assertThat(out.text).isEqualTo("first line\nsecond line")
    }

    @Test
    fun `render text equals stripped markup for added-word input`() {
        val m = "And God <a>said</a>, Let there <a>be</a> light"
        assertThat(VerseRenderer.render(m).text).isEqualTo(MarkupStripper.strip(m))
    }
}

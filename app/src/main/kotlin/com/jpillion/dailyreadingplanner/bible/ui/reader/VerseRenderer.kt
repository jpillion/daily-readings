package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import com.jpillion.dailyreadingplanner.bible.data.markup.BibleMarkup

/**
 * VC-T2 / ESpec-v3 §4.4, D-V3-6 — renders one verse's closed-tag markup to an [AnnotatedString].
 * Pure and JVM-unit-testable (no Compose runtime needed — it only builds the string + spans).
 *
 * The vocabulary is the closed [BibleMarkup] set. V3.0 emits only `<a>` (italic added words);
 * `<w>` and `<l/>` are recognized from day one so enabling them later is artifact-only:
 *  - `<a>…</a>`  → inner text in an italic [SpanStyle] (P0, the only emphasis V3.0 emits).
 *  - `<w>…</w>`  → inner text kept, NO span in V3.0 (red-letter reserved, P1 — see the branch).
 *  - `<l/>`      → a newline (poetic line break, P1; the asset does not emit it in V3.0).
 *  - plain text  → appended verbatim.
 *
 * The text content (with `<l/>` as a space) equals `MarkupStripper.strip(markup)` for the
 * added/words cases — inner text is never dropped or duplicated. Unknown `<...>` tags never
 * appear (gate-enforced); for robustness any unrecognized tag is dropped, inner text kept.
 *
 * The tag set is tiny and closed, so this is a small hand parser — no XML library (matches the
 * `MarkupStripper` discipline of operating directly on the markup string).
 */
object VerseRenderer {
    private val TAG = Regex("<(/?)([a-zA-Z]+)\\s*(/?)>")

    fun render(markup: String): AnnotatedString =
        buildAnnotatedString {
            var index = 0
            var italicDepth = 0
            for (match in TAG.findAll(markup)) {
                // Emit the plain text since the previous tag.
                if (match.range.first > index) {
                    appendStyled(markup.substring(index, match.range.first), italicDepth)
                }
                val closing = match.groupValues[1] == "/"
                val name = match.groupValues[2]
                val selfClosing = match.groupValues[3] == "/"
                when (name) {
                    BibleMarkup.LINE_BREAK -> append("\n") // <l/> poetic line break (P1, reserved).
                    BibleMarkup.ADDED ->
                        if (selfClosing) {
                            Unit
                        } else if (closing) {
                            italicDepth--
                        } else {
                            italicDepth++
                        }
                    BibleMarkup.WORDS_OF_CHRIST -> Unit // <w> red-letter: P1, no span in V3.0; inner text kept.
                    else -> Unit // unknown tag: drop the tag, keep inner text (gate guarantees none).
                }
                index = match.range.last + 1
            }
            if (index < markup.length) appendStyled(markup.substring(index), italicDepth)
        }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStyled(
        text: String,
        italicDepth: Int,
    ) {
        if (italicDepth > 0) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text) }
        } else {
            append(text)
        }
    }
}

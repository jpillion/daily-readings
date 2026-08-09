package com.jpillion.dailyreadingplanner.data.reference

import assertk.assertThat
import assertk.assertions.isEmpty
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import org.junit.Test
import java.net.URLEncoder

/**
 * ⚠️ **JVM-ONLY BY DESIGN. Do not move this file to `commonTest`.**
 *
 * It imports `java.net.URLEncoder` on purpose: it is the *differential* proof that
 * [PercentEncoder] reproduces the encoder p1-03 removed, **byte for byte**, over a far wider corpus
 * than a human would write literals for. Its whole value is that the reference implementation is
 * the real one.
 *
 * **Delete it at the module split** (`p2-04`), when `ProviderUrlBuilder` moves to `shared/domain`
 * and `java.*` stops being available. Nothing is lost: [PercentEncoderTest] carries the contract
 * forward as literals, and `ProviderUrlBuilderTest` carries the live-verified URLs.
 *
 * Why this exists rather than "the tests were green": `ProviderUrlBuilderTest` executes 430 URL
 * expectations, but only 8 of them reach this encoder at all — the rest are BLB, YouVersion and
 * MySword, which build their URLs from tokens that need no encoding. Those 8 are enough to catch
 * space-becomes-`%20` and nothing else. This closes the rest of the alphabet.
 */
class PercentEncoderJvmDifferentialTest {
    private fun reference(): String = "java.net.URLEncoder.encode(value, \"UTF-8\")"

    private fun diffs(inputs: List<String>): List<String> =
        inputs.mapNotNull { input ->
            val expected = URLEncoder.encode(input, "UTF-8")
            val actual = PercentEncoder.encode(input)
            if (expected == actual) null else "\"$input\" -> ${reference()}=\"$expected\" but got \"$actual\""
        }

    @Test
    fun `every single character in the ASCII range encodes identically to the encoder p1-03 removed`() {
        assertThat(diffs((0..127).map { it.toChar().toString() })).isEmpty()
    }

    @Test
    fun `non-ASCII and mixed strings encode identically to the encoder p1-03 removed`() {
        val inputs =
            listOf(
                "é",
                "世界",
                "😀",
                "Ærøskøbing",
                "Καινή Διαθήκη",
                "a é 世 😀 mix with spaces",
                "",
                " ",
                "   ",
                "%20",
                "already+encoded",
                "a\nb\tc",
            )
        assertThat(diffs(inputs)).isEmpty()
    }

    /**
     * The corpus that actually ships. Every string [ProviderUrlBuilder] can hand the encoder is one
     * of these: a Bible Gateway passage search built from canonical book names, chapter numbers and
     * verse numbers. All 66 books, both range and single forms, plus the verse form.
     */
    @Test
    fun `every bible gateway search string this app can build encodes identically`() {
        val searches =
            BookCatalog.books.flatMap { book ->
                listOf(
                    "${book.canonicalName} 1",
                    "${book.canonicalName} 1-${book.chapterCount}",
                    "${book.canonicalName} ${book.chapterCount}",
                    "${book.canonicalName} 1:1",
                    "${book.canonicalName} ${book.chapterCount}:176",
                    "${book.canonicalName} 1,${book.canonicalName} 5",
                )
            }
        assertThat(diffs(searches)).isEmpty()
    }

    /**
     * The end-to-end statement, and the one that matters: the whole URL, not just the encoder.
     * Builds every reachable Bible Gateway URL with [PercentEncoder] and re-builds it with the
     * removed encoder, and requires the two to be identical strings.
     */
    @Test
    fun `every bible gateway url is byte-identical to the one the removed encoder produced`() {
        val builder = ProviderUrlBuilder()
        val diffs =
            BookCatalog.books
                .flatMap { book ->
                    val single = Portion(3, listOf(Reference(book, 1)))
                    val range = Portion(3, (1..minOf(3, book.chapterCount)).map { Reference(book, it) })
                    listOf(
                        builder.build(ExternalBibleApp.BIBLE_GATEWAY, single) to
                            gatewayUrlWithRemovedEncoder("${book.canonicalName} 1"),
                        builder.build(ExternalBibleApp.BIBLE_GATEWAY, range) to
                            gatewayUrlWithRemovedEncoder(rangeSearch(book.canonicalName, book.chapterCount)),
                        builder.buildVerse(ExternalBibleApp.BIBLE_GATEWAY, Reference(book, 1), 1) to
                            gatewayUrlWithRemovedEncoder("${book.canonicalName} 1:1"),
                    )
                }.filter { (actual, expected) -> actual != expected }
                .map { (actual, expected) -> "expected \"$expected\" but got \"$actual\"" }

        assertThat(diffs).isEmpty()
    }

    private fun rangeSearch(
        name: String,
        chapterCount: Int,
    ): String = if (chapterCount == 1) "$name 1" else "$name 1-${minOf(3, chapterCount)}"

    private fun gatewayUrlWithRemovedEncoder(search: String): String =
        "https://www.biblegateway.com/passage/?search=${URLEncoder.encode(search, "UTF-8")}&version=KJV"
}

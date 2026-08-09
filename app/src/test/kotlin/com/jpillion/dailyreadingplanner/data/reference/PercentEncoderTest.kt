package com.jpillion.dailyreadingplanner.data.reference

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

/**
 * p1-03 / ADR-0014 Amendment A1 — the literal contract of [PercentEncoder].
 *
 * **Portable by construction:** every expectation is a literal, so this file moves to `commonTest`
 * unchanged. `PercentEncoderJvmDifferentialTest` is the separate, deliberately JVM-only proof that
 * these literals are `java.net.URLEncoder`'s output rather than this author's opinion.
 *
 * The encoder is **form encoding**, not RFC 3986 — see the class KDoc for why that is the
 * requirement and not a defect.
 */
class PercentEncoderTest {
    @Test
    fun `a space becomes plus - not percent 20 - because the live-verified corpus contains plus`() {
        assertThat(PercentEncoder.encode(" ")).isEqualTo("+")
        assertThat(PercentEncoder.encode("Song of Solomon 8")).isEqualTo("Song+of+Solomon+8")
    }

    @Test
    fun `the characters the shipped urls actually contain encode to the shipped bytes`() {
        // Every non-alphanumeric character reachable from a book name plus chapter and verse.
        assertThat(PercentEncoder.encode(",")).isEqualTo("%2C")
        assertThat(PercentEncoder.encode(":")).isEqualTo("%3A")
        assertThat(PercentEncoder.encode("-")).isEqualTo("-")
        assertThat(PercentEncoder.encode("2 John 1,3 John 1")).isEqualTo("2+John+1%2C3+John+1")
        assertThat(PercentEncoder.encode("Genesis 1-2")).isEqualTo("Genesis+1-2")
        assertThat(PercentEncoder.encode("Genesis 1:1")).isEqualTo("Genesis+1%3A1")
    }

    @Test
    fun `reserved and delimiter characters are percent-encoded with uppercase hex`() {
        assertThat(PercentEncoder.encode("+")).isEqualTo("%2B")
        assertThat(PercentEncoder.encode("&")).isEqualTo("%26")
        assertThat(PercentEncoder.encode("?")).isEqualTo("%3F")
        assertThat(PercentEncoder.encode("/")).isEqualTo("%2F")
        assertThat(PercentEncoder.encode(";")).isEqualTo("%3B")
        assertThat(PercentEncoder.encode("=")).isEqualTo("%3D")
        assertThat(PercentEncoder.encode("%")).isEqualTo("%25")
    }

    @Test
    fun `the unreserved set passes through untouched`() {
        val unreserved = "ABCXYZabcxyz0189-_."
        assertThat(PercentEncoder.encode(unreserved)).isEqualTo(unreserved)
    }

    /**
     * The two characters where form encoding and RFC 3986 disagree in the *other* direction.
     * Pinned so that "correcting" this encoder to RFC 3986 is a red test rather than a quiet change
     * to a live-verified URL corpus. Neither can occur in a book name.
     */
    @Test
    fun `form encoding keeps asterisk and encodes tilde - the reverse of RFC 3986`() {
        assertThat(PercentEncoder.encode("*")).isEqualTo("*")
        assertThat(PercentEncoder.encode("~")).isEqualTo("%7E")
    }

    @Test
    fun `a multi-byte character is encoded per UTF-8 byte`() {
        // U+00E9 -> C3 A9; U+4E16 -> E4 B8 96; U+1F600 -> F0 9F 98 80.
        assertThat(PercentEncoder.encode("é")).isEqualTo("%C3%A9")
        assertThat(PercentEncoder.encode("世")).isEqualTo("%E4%B8%96")
        assertThat(PercentEncoder.encode("😀")).isEqualTo("%F0%9F%98%80")
    }

    @Test
    fun `the empty string encodes to the empty string`() {
        assertThat(PercentEncoder.encode("")).isEqualTo("")
    }
}

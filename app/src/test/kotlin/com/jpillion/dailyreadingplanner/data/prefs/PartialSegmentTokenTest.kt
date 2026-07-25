package com.jpillion.dailyreadingplanner.data.prefs

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * SEG-2: the D-SEG-4 token codec. `parse` is total — ANY malformed input yields null, never an
 * exception, so a token from a future/older build is dropped instead of crashing the day screen.
 */
class PartialSegmentTokenTest {
    @Test
    fun `round-trips every field, including negative epoch days and multi-digit indexes`() {
        val cases =
            listOf(
                PartialSegmentToken.Parsed("bible_companion", 20_640L, 1, 0),
                PartialSegmentToken.Parsed("mcheyne", 0L, 4, 12),
                // Pre-1970 dates encode as negative epoch days; the codec must not lose the sign.
                PartialSegmentToken.Parsed("chronological", -1L, 1, 5),
            )
        cases.forEach { expected ->
            val token =
                PartialSegmentToken.encode(
                    expected.planId,
                    expected.epochDay,
                    expected.streamNumber,
                    expected.segmentIndex,
                )
            assertThat(PartialSegmentToken.parse(token)).isEqualTo(expected)
        }
    }

    @Test
    fun `encodes the documented pipe-delimited shape`() {
        // Pinned literally: the on-disk format is a contract with every build that reads the cache.
        assertThat(PartialSegmentToken.encode("bible_companion", 20_640L, 2, 1))
            .isEqualTo("bible_companion|20640|2|1")
    }

    @Test
    fun `encode rejects a plan id containing the delimiter`() {
        // A piped plan id would make the token ambiguous to parse — fail at the source.
        val thrown =
            runCatching {
                PartialSegmentToken.encode("bible|companion", 20_640L, 1, 0)
            }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `MUTATION every malformed form parses to null and never throws`() {
        val malformed =
            listOf(
                "" to "empty",
                "bible_companion" to "one field",
                "bible_companion|20640" to "two fields",
                "bible_companion|20640|1" to "three fields",
                "bible_companion|20640|1|0|9" to "five fields",
                "|20640|1|0" to "blank plan id",
                "   |20640|1|0" to "whitespace-only plan id",
                "bible_companion|notaday|1|0" to "non-numeric epoch day",
                "bible_companion|20640|x|0" to "non-numeric stream",
                "bible_companion|20640|1|y" to "non-numeric segment index",
                "bible_companion|20640.5|1|0" to "decimal epoch day",
                "bible_companion|99999999999999999999|1|0" to "epoch day overflowing Long",
                "bible_companion|20640|99999999999|0" to "stream overflowing Int",
            )
        malformed.forEach { (token, why) ->
            val outcome = runCatching { PartialSegmentToken.parse(token) }
            assertWithMessage("parse must never throw ($why): '%s'", token)
                .that(outcome.exceptionOrNull())
                .isNull()
            assertWithMessage("must parse to null ($why): '%s'", token)
                .that(outcome.getOrThrow())
                .isNull()
        }
    }
}

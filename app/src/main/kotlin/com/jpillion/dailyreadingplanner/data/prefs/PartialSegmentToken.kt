package com.jpillion.dailyreadingplanner.data.prefs

/**
 * Codec for the partial-check cache tokens (D-SEG-4): `"<planId>|<epochDay>|<stream>|<segmentIndex>"`.
 *
 * Pure and Android-free — the one home for the token format, so the store and every reader encode
 * and decode it identically. [parse] is deliberately total: ANY malformed input yields `null`
 * rather than an exception, so a token written by a future (or older) build is simply dropped
 * instead of crashing or corrupting the day screen. That self-healing property is what lets this
 * cache stay schema-less.
 */
object PartialSegmentToken {
    private const val DELIMITER = '|'
    private const val FIELD_COUNT = 4

    /**
     * Builds the canonical token. [planId] must not contain the [DELIMITER] — a piped plan id
     * would make tokens ambiguous to [parse], so it is rejected at the source rather than
     * silently producing an undecodable token.
     */
    fun encode(
        planId: String,
        epochDay: Long,
        streamNumber: Int,
        segmentIndex: Int,
    ): String {
        require(!planId.contains(DELIMITER)) {
            "planId must not contain '$DELIMITER' (the token delimiter): $planId"
        }
        return "$planId$DELIMITER$epochDay$DELIMITER$streamNumber$DELIMITER$segmentIndex"
    }

    /**
     * Decodes a token, or returns `null` for anything that is not exactly four well-formed fields
     * (wrong field count, blank plan id, non-numeric epoch day / stream / segment index). Never throws.
     */
    fun parse(token: String): Parsed? {
        val fields = token.split(DELIMITER)
        if (fields.size != FIELD_COUNT) return null
        val planId = fields[0]
        if (planId.isBlank()) return null
        val epochDay = fields[1].toLongOrNull() ?: return null
        val streamNumber = fields[2].toIntOrNull() ?: return null
        val segmentIndex = fields[3].toIntOrNull() ?: return null
        return Parsed(planId, epochDay, streamNumber, segmentIndex)
    }

    data class Parsed(
        val planId: String,
        val epochDay: Long,
        val streamNumber: Int,
        val segmentIndex: Int,
    )
}

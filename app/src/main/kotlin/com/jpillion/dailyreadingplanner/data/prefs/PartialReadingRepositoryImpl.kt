package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * DataStore-backed [PartialReadingRepository] (D-SEG-4) over the shared preferences store — the
 * same file every other setting lives in, under the single key `partial_reading_segments`.
 *
 * No Room involvement of any kind: this is a cosmetic cache, and D-SEG-3 keeps the progress schema
 * exactly as it is (one mark per plan/date/stream).
 */
class PartialReadingRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val clock: Clock,
    ) : PartialReadingRepository {
        override val partialSegments: Flow<Set<String>> =
            dataStore.data.map { preferences -> preferences[PARTIAL_SEGMENTS_KEY] ?: emptySet() }

        override suspend fun setPartialSegments(
            planId: String,
            date: LocalDate,
            streamNumber: Int,
            segmentIndexes: Set<Int>,
        ) {
            val epochDay = date.toEpochDay()
            dataStore.edit { preferences ->
                val current = preferences[PARTIAL_SEGMENTS_KEY] ?: emptySet()
                // Replace (never merge) this triple's tokens; other plans/dates/streams untouched.
                val others =
                    current.filterNot { token ->
                        val parsed = PartialSegmentToken.parse(token)
                        parsed != null &&
                            parsed.planId == planId &&
                            parsed.epochDay == epochDay &&
                            parsed.streamNumber == streamNumber
                    }
                val replaced =
                    others +
                        segmentIndexes.map { index ->
                            PartialSegmentToken.encode(planId, epochDay, streamNumber, index)
                        }
                val pruned = prune(replaced)
                if (pruned.isEmpty()) {
                    // Store nothing rather than an empty set — an untouched install has no key.
                    preferences.remove(PARTIAL_SEGMENTS_KEY)
                } else {
                    preferences[PARTIAL_SEGMENTS_KEY] = pruned
                }
            }
        }

        /**
         * D-SEG-5: bounded growth. Keeps a token iff it parses AND its epoch day is no more than
         * [RETENTION_DAYS] days before today. Future-dated tokens are kept — a user can swipe
         * forward and check ahead. Unparseable tokens are dropped (self-healing).
         */
        private fun prune(tokens: Collection<String>): Set<String> {
            val cutoffEpochDay = LocalDate.now(clock).toEpochDay() - RETENTION_DAYS
            return tokens
                .filter { token ->
                    val parsed = PartialSegmentToken.parse(token)
                    parsed != null && parsed.epochDay >= cutoffEpochDay
                }.toSet()
        }

        companion object {
            /** Tokens dated more than this many days before today are dropped on the next write. */
            const val RETENTION_DAYS = 400L

            internal val PARTIAL_SEGMENTS_KEY = stringSetPreferencesKey("partial_reading_segments")
        }
    }

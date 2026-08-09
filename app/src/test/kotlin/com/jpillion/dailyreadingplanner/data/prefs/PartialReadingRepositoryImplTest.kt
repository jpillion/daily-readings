package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.jpillion.dailyreadingplanner.platform.DateProvider
import com.jpillion.dailyreadingplanner.platform.FakeDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * SEG-2: the D-SEG-4 cosmetic partial-check cache over the shared DataStore.
 *
 * This store is deliberately NOT a progress record (D-SEG-3) — Room is untouched. The behaviours
 * pinned here are the ones the day screen depends on: replace-not-merge per (plan, date, stream),
 * isolation between triples, and the D-SEG-5 retention boundary.
 */
class PartialReadingRepositoryImplTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** T — "today" for every test; a fixed Clock so the retention boundary is exact. */
    private val today = LocalDate(2026, 7, 25)
    private val dateProvider: DateProvider =
        FakeDateProvider(today)

    private val key = stringSetPreferencesKey("partial_reading_segments")

    private fun TestScope.createDataStore(scope: CoroutineScope): DataStore<Preferences> {
        val file = File(tmp.root, "settings.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    private fun cacheTest(block: suspend (PartialReadingRepositoryImpl, DataStore<Preferences>) -> Unit) =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val dataStore = createDataStore(scope)
            try {
                block(PartialReadingRepositoryImpl(dataStore, dateProvider), dataStore)
            } finally {
                scope.cancel()
            }
        }

    private fun token(
        planId: String = "bible_companion",
        date: LocalDate = today,
        stream: Int = 1,
        segment: Int,
    ) = PartialSegmentToken.encode(planId, date.toEpochDays(), stream, segment)

    @Test
    fun `an untouched store reads back the empty set`() =
        cacheTest { repository, _ ->
            assertThat(repository.partialSegments.first()).isEmpty()
        }

    @Test
    fun `writing segment indexes stores exactly those tokens`() =
        cacheTest { repository, _ ->
            repository.setPartialSegments("bible_companion", today, streamNumber = 1, setOf(0, 2))
            assertThat(repository.partialSegments.first())
                .containsExactlyInAnyOrder(token(segment = 0), token(segment = 2))
        }

    @Test
    fun `MUTATION overwriting the same triple replaces rather than merges`() =
        cacheTest { repository, _ ->
            // The day screen writes the WHOLE current set for a reading. If this merged, an
            // unchecked segment would stay checked forever.
            repository.setPartialSegments("bible_companion", today, streamNumber = 1, setOf(0, 2))
            repository.setPartialSegments("bible_companion", today, streamNumber = 1, setOf(1))
            assertThat(repository.partialSegments.first()).containsExactlyInAnyOrder(token(segment = 1))
        }

    @Test
    fun `writing an empty set clears that triple and leaves other triples intact`() =
        cacheTest { repository, _ ->
            repository.setPartialSegments("bible_companion", today, streamNumber = 1, setOf(0, 1))
            repository.setPartialSegments("bible_companion", today, streamNumber = 2, setOf(3))
            repository.setPartialSegments("bible_companion", today, streamNumber = 1, emptySet())
            assertThat(repository.partialSegments.first())
                .containsExactlyInAnyOrder(token(stream = 2, segment = 3))
        }

    @Test
    fun `clearing the last tokens removes the key entirely rather than storing an empty set`() =
        cacheTest { repository, dataStore ->
            repository.setPartialSegments("bible_companion", today, streamNumber = 1, setOf(0))
            repository.setPartialSegments("bible_companion", today, streamNumber = 1, emptySet())
            assertThat(dataStore.data.first().contains(key)).isFalse()
            assertThat(repository.partialSegments.first()).isEmpty()
        }

    @Test
    fun `MUTATION a write touches only its own plan - date and stream`() =
        cacheTest { repository, _ ->
            val otherPlan = token(planId = "mcheyne", segment = 0)
            val otherDate = token(date = today.minus(1, DateTimeUnit.DAY), segment = 0)
            val otherStream = token(stream = 3, segment = 0)
            repository.setPartialSegments("mcheyne", today, streamNumber = 1, setOf(0))
            repository.setPartialSegments(
                "bible_companion",
                today.minus(1, DateTimeUnit.DAY),
                streamNumber = 1,
                setOf(0),
            )
            repository.setPartialSegments("bible_companion", today, streamNumber = 3, setOf(0))

            // Overwrite (bible_companion, today, stream 1) — the three neighbours must survive.
            repository.setPartialSegments("bible_companion", today, streamNumber = 1, setOf(5))

            assertThat(repository.partialSegments.first())
                .containsExactlyInAnyOrder(otherPlan, otherDate, otherStream, token(segment = 5))
        }

    // --- D-SEG-5: retention. The cache is bounded; the boundary itself is the mutation target. ---

    @Test
    fun `MUTATION a token dated exactly the retention bound survives a later write`() =
        cacheTest { repository, dataStore ->
            // Keep iff epochDay >= today - RETENTION_DAYS. T-400 is the last day kept.
            val edge =
                token(date = today.minus(PartialReadingRepositoryImpl.RETENTION_DAYS, DateTimeUnit.DAY), segment = 0)
            dataStore.edit { it[key] = setOf(edge) }

            repository.setPartialSegments("bible_companion", today, streamNumber = 2, setOf(1))

            assertThat(repository.partialSegments.first())
                .containsExactlyInAnyOrder(edge, token(stream = 2, segment = 1))
        }

    @Test
    fun `MUTATION a token one day past the retention bound is pruned on the next write`() =
        cacheTest { repository, dataStore ->
            val stale =
                token(
                    date = today.minus(PartialReadingRepositoryImpl.RETENTION_DAYS + 1, DateTimeUnit.DAY),
                    segment = 0,
                )
            dataStore.edit { it[key] = setOf(stale) }

            repository.setPartialSegments("bible_companion", today, streamNumber = 2, setOf(1))

            assertThat(repository.partialSegments.first())
                .containsExactlyInAnyOrder(token(stream = 2, segment = 1))
        }

    @Test
    fun `a future-dated token survives - the user can swipe ahead and check early`() =
        cacheTest { repository, dataStore ->
            val ahead = token(date = today.plus(10, DateTimeUnit.DAY), segment = 0)
            dataStore.edit { it[key] = setOf(ahead) }

            repository.setPartialSegments("bible_companion", today, streamNumber = 2, setOf(1))

            assertThat(repository.partialSegments.first())
                .containsExactlyInAnyOrder(ahead, token(stream = 2, segment = 1))
        }

    @Test
    fun `pinned retention window is 400 days`() {
        // The exact bound is a product decision (D-SEG-5), not an implementation detail.
        assertThat(PartialReadingRepositoryImpl.RETENTION_DAYS).isEqualTo(400L)
    }

    @Test
    fun `a malformed stored token never crashes a read and is dropped on the next write`() =
        cacheTest { repository, dataStore ->
            // A token from a future/older build. The cache is self-healing: reads pass it through
            // untouched (callers decode defensively) and the next write drops it.
            val junk = "not-a-token"
            dataStore.edit { it[key] = setOf(junk, token(segment = 0)) }
            assertThat(repository.partialSegments.first()).contains(junk)

            repository.setPartialSegments("bible_companion", today, streamNumber = 2, setOf(1))

            assertThat(repository.partialSegments.first())
                .containsExactlyInAnyOrder(token(segment = 0), token(stream = 2, segment = 1))
        }
}

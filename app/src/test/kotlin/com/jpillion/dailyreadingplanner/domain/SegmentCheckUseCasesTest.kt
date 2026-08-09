package com.jpillion.dailyreadingplanner.domain

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.plan.PlanRegistry
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import com.jpillion.dailyreadingplanner.testing.FakePartialReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test

/**
 * SEG-3 — the three use cases that join the pure [SegmentCheckPolicy] to storage (sprint-00P).
 *
 * These assert the **resulting storage state** (the real Room-side mark AND the DataStore token
 * set), not the policy's return value — the policy itself is pinned separately by
 * `SegmentCheckPolicyTest`. Two tests additionally pin the load-bearing WRITE ORDER between the two
 * stores via the shared timeline recorded by [FakePartialReadingRepository] and
 * [RecordingProgressRepository].
 *
 * Mutation targets these pins kill:
 *  - dropping the plan filter in `partialSegmentsByStream` → `partials for another plan are
 *    filtered out` reddens.
 *  - dropping the date filter → `partials for another date are filtered out` reddens.
 *  - `ToggleSegmentCheckUseCase` skipping either write → the paired mark/token assertions redden.
 *  - `MarkSegmentReadOnOpenUseCase` calling the Room mark unconditionally, or falling through to a
 *    toggle → the two one-way-SET pins redden.
 *  - flipping either branch of the write-order rule → the two order pins redden.
 */
class SegmentCheckUseCasesTest {
    private val planId = PlanRegistry.DEFAULT_PLAN_ID
    private val otherPlanId = "mcheyne"
    private val date = LocalDate(2026, 7, 25)
    private val otherDate = date.plus(1, DateTimeUnit.DAY)

    /** Shared ordered timeline of writes across BOTH stores — the write-order pin's instrument. */
    private val writeLog = mutableListOf<String>()
    private val partials = FakePartialReadingRepository(writeLog)
    private val progress = FakeProgressRepository()
    private val recordingProgress = RecordingProgressRepository(progress, writeLog)
    private val activePlan = FakeActivePlanRepository()

    private val getPartials = GetPartialSegmentsUseCase(partials, activePlan)
    private val toggleSegment =
        ToggleSegmentCheckUseCase(
            ToggleReadingUseCase(recordingProgress, activePlan),
            partials,
            activePlan,
        )
    private val markSegmentOnOpen =
        MarkSegmentReadOnOpenUseCase(
            MarkReadOnOpenUseCase(recordingProgress, activePlan),
            partials,
            activePlan,
        )

    private fun tokensFor(stream: Int) = partials.segmentsFor(planId, date, stream)

    // ---- GetPartialSegmentsUseCase ---------------------------------------------------------

    @Test
    fun `groups the date's partial segments by stream`() =
        runTest {
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(0, 2))
            partials.setPartialSegments(planId, date, streamNumber = 3, segmentIndexes = setOf(1))

            assertThat(getPartials(date).first())
                .isEqualTo(mapOf(1 to setOf(0, 2), 3 to setOf(1)))
        }

    @Test
    fun `partials for another date are filtered out`() =
        runTest {
            partials.setPartialSegments(planId, otherDate, streamNumber = 1, segmentIndexes = setOf(0))
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(1))

            assertThat(getPartials(date).first()).isEqualTo(mapOf(1 to setOf(1)))
        }

    @Test
    fun `partials for another plan are filtered out`() =
        runTest {
            partials.setPartialSegments(otherPlanId, date, streamNumber = 1, segmentIndexes = setOf(0))
            partials.setPartialSegments(planId, date, streamNumber = 2, segmentIndexes = setOf(1))

            assertThat(getPartials(date).first()).isEqualTo(mapOf(2 to setOf(1)))
        }

    @Test
    fun `malformed tokens are silently ignored`() =
        runTest {
            // Self-healing cache: junk written by a future/older build must never crash or corrupt
            // the day screen — it is simply dropped.
            partials.seedRaw("nonsense", "$planId|not-a-number|1|0", "$planId|${date.toEpochDays()}|1")
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(0))

            assertThat(getPartials(date).first()).isEqualTo(mapOf(1 to setOf(0)))
        }

    @Test
    fun `a stream with no partial segments is absent from the map`() =
        runTest {
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(0))

            val result = getPartials(date).first()
            assertThat(result.keys).containsExactlyInAnyOrder(1)
            assertThat(result[2]).isNull() // callers use `?: emptySet()`
        }

    @Test
    fun `re-emits with the new plan's partials when the active plan changes`() =
        runTest {
            val switchable = SwitchableActivePlanRepository(planId)
            val useCase = GetPartialSegmentsUseCase(partials, switchable)
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(0))
            partials.setPartialSegments(otherPlanId, date, streamNumber = 1, segmentIndexes = setOf(1, 2))

            useCase(date).test {
                assertThat(awaitItem()).isEqualTo(mapOf(1 to setOf(0)))
                switchable.planId.value = otherPlanId
                assertThat(awaitItem()).isEqualTo(mapOf(1 to setOf(1, 2)))
            }
        }

    // ---- ToggleSegmentCheckUseCase: the transition table, applied to storage ----------------

    @Test
    fun `first check of three writes only a token - no real mark`() =
        runTest {
            toggleSegment(date, streamNumber = 1, segmentIndex = 0, segmentCount = 3, streamMarked = false)

            assertThat(progress.marksFor(date)).isEmpty()
            assertThat(tokensFor(1)).containsExactlyInAnyOrder(0)
        }

    @Test
    fun `the last check of three sets the real mark and clears every token`() =
        runTest {
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(0, 2))

            toggleSegment(date, streamNumber = 1, segmentIndex = 1, segmentCount = 3, streamMarked = false)

            assertThat(progress.marksFor(date)).containsExactlyInAnyOrder(1)
            assertThat(tokensFor(1)).isEmpty()
        }

    @Test
    fun `tapping a PARTIAL segment removes its token and leaves the mark unset`() =
        runTest {
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(0, 2))

            toggleSegment(date, streamNumber = 1, segmentIndex = 0, segmentCount = 3, streamMarked = false)

            assertThat(progress.marksFor(date)).isEmpty()
            assertThat(tokensFor(1)).containsExactlyInAnyOrder(2)
        }

    @Test
    fun `tapping a COMPLETE three-segment reading clears the mark and leaves the others PARTIAL`() =
        runTest {
            progress.setRead(date, streamNumber = 1, isRead = true)

            toggleSegment(date, streamNumber = 1, segmentIndex = 1, segmentCount = 3, streamMarked = true)

            assertThat(progress.marksFor(date)).isEmpty()
            assertThat(tokensFor(1)).containsExactlyInAnyOrder(0, 2)
        }

    @Test
    fun `N==1 parity - checking toggles the real mark and never writes a token`() =
        runTest {
            toggleSegment(date, streamNumber = 2, segmentIndex = 0, segmentCount = 1, streamMarked = false)

            assertThat(progress.marksFor(date)).containsExactlyInAnyOrder(2)
            assertThat(tokensFor(2)).isEmpty()
            assertThat(partials.stored.value).isEmpty()
        }

    @Test
    fun `N==1 parity - unchecking clears the real mark and never writes a token`() =
        runTest {
            progress.setRead(date, streamNumber = 2, isRead = true)

            toggleSegment(date, streamNumber = 2, segmentIndex = 0, segmentCount = 1, streamMarked = true)

            assertThat(progress.marksFor(date)).isEmpty()
            assertThat(tokensFor(2)).isEmpty()
            assertThat(partials.stored.value).isEmpty()
        }

    // ---- MarkSegmentReadOnOpenUseCase: Sprint-00O one-way SET at segment level --------------

    @Test
    fun `opening an UNCHECKED segment makes it PARTIAL without marking the stream`() =
        runTest {
            markSegmentOnOpen(date, streamNumber = 1, segmentIndex = 2, segmentCount = 3, streamMarked = false)

            assertThat(progress.marksFor(date)).isEmpty()
            assertThat(tokensFor(1)).containsExactlyInAnyOrder(2)
        }

    @Test
    fun `opening the last UNCHECKED segment completes the stream and clears the tokens`() =
        runTest {
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(0, 1))

            markSegmentOnOpen(date, streamNumber = 1, segmentIndex = 2, segmentCount = 3, streamMarked = false)

            assertThat(progress.marksFor(date)).containsExactlyInAnyOrder(1)
            assertThat(tokensFor(1)).isEmpty()
        }

    @Test
    fun `opening an already-PARTIAL segment changes nothing`() =
        runTest {
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(0, 2))

            markSegmentOnOpen(date, streamNumber = 1, segmentIndex = 0, segmentCount = 3, streamMarked = false)

            assertThat(progress.marksFor(date)).isEmpty()
            assertThat(tokensFor(1)).containsExactlyInAnyOrder(0, 2)
        }

    @Test
    fun `opening an already-COMPLETE reading never unmarks it`() =
        runTest {
            // D-O-1 at segment level: the open side effect is one-way SET. Re-opening any segment
            // of a completed reading must leave the real mark set and the token cache empty.
            progress.setRead(date, streamNumber = 1, isRead = true)

            markSegmentOnOpen(date, streamNumber = 1, segmentIndex = 1, segmentCount = 3, streamMarked = true)

            assertThat(progress.marksFor(date)).containsExactlyInAnyOrder(1)
            assertThat(tokensFor(1)).isEmpty()
            assertThat(writeLog).doesNotContain(RecordingProgressRepository.UNMARK_WRITE)
        }

    // ---- Write order (the mark-dominant intermediate-frame rule) ----------------------------

    @Test
    fun `completing writes the real mark BEFORE the tokens`() =
        runTest {
            // stateFor is mark-dominant, so marking first makes the intermediate frame
            // (marked + stale tokens) read as COMPLETE — the END state. The opposite order clears
            // the tokens while the mark is still false: a one-frame "all unchecked" flash.
            partials.setPartialSegments(planId, date, streamNumber = 1, segmentIndexes = setOf(0, 2))
            writeLog.clear()

            toggleSegment(date, streamNumber = 1, segmentIndex = 1, segmentCount = 3, streamMarked = false)

            assertThat(writeLog)
                .containsExactlyInAnyOrder(
                    RecordingProgressRepository.MARK_WRITE,
                    FakePartialReadingRepository.TOKENS_WRITE,
                )
        }

    @Test
    fun `clearing the mark writes the tokens BEFORE the mark`() =
        runTest {
            // The intermediate frame (still marked + new tokens) reads as COMPLETE — the START
            // state. Mark-first would flash "all unchecked" before the survivors reappear PARTIAL.
            progress.setRead(date, streamNumber = 1, isRead = true)
            writeLog.clear()

            toggleSegment(date, streamNumber = 1, segmentIndex = 1, segmentCount = 3, streamMarked = true)

            assertThat(writeLog)
                .containsExactly(
                    FakePartialReadingRepository.TOKENS_WRITE,
                    RecordingProgressRepository.UNMARK_WRITE,
                )
        }
}

/**
 * Wraps [FakeProgressRepository] to append every real mark write to a shared timeline, so a test
 * can observe how the Room-side mark interleaves with the DataStore token write. Everything else
 * delegates untouched.
 */
private class RecordingProgressRepository(
    private val delegate: FakeProgressRepository,
    private val writeLog: MutableList<String>,
) : ProgressRepository by delegate {
    override suspend fun setRead(
        date: LocalDate,
        streamNumber: Int,
        isRead: Boolean,
        planId: String,
    ) {
        writeLog += if (isRead) MARK_WRITE else UNMARK_WRITE
        delegate.setRead(date, streamNumber, isRead, planId)
    }

    companion object {
        const val MARK_WRITE = "mark"
        const val UNMARK_WRITE = "unmark"
    }
}

/**
 * An [ActivePlanRepository] whose id can change mid-test — the shared `FakeActivePlanRepository`
 * fixes its plan at construction. Local to this file deliberately: the shared fake is being edited
 * by other tickets this sprint, and this is the only test that needs a live switch.
 */
private class SwitchableActivePlanRepository(
    initialPlanId: String,
) : ActivePlanRepository {
    val planId = MutableStateFlow(initialPlanId)

    override val activePlanId: Flow<String> = planId

    override val activeDescriptor: Flow<PlanDescriptor> = MutableStateFlow(bibleCompanionDescriptor)
}

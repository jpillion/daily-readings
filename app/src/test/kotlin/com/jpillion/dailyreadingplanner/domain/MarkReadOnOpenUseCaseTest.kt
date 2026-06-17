package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/**
 * Sprint 00O (D-O-1): mark-on-open is one-way (never unmarks), idempotent, and writes to the
 * active plan's partition. These pin the use case directly so the `isRead = true` invariant has
 * a dedicated guard independent of the ViewModel wiring.
 */
class MarkReadOnOpenUseCaseTest {
    private val progress = FakeProgressRepository()
    private val activePlan = FakeActivePlanRepository()
    private val useCase = MarkReadOnOpenUseCase(progress, activePlan)
    private val date = LocalDate.of(2026, 6, 10)

    @Test
    fun `marks the given stream read for the given date`() =
        runTest {
            useCase(date, streamNumber = 2)
            assertThat(progress.marksFor(date)).containsExactly(2)
        }

    @Test
    fun `is idempotent - re-opening an already-read reading never unmarks it`() =
        runTest {
            progress.setRead(date, 2, true)
            useCase(date, streamNumber = 2)
            useCase(date, streamNumber = 2)
            // Still read, never toggled off by the open side effect.
            assertThat(progress.marksFor(date)).containsExactly(2)
        }

    @Test
    fun `only the tapped stream is affected, other streams untouched`() =
        runTest {
            progress.setRead(date, 1, true)
            useCase(date, streamNumber = 3)
            assertThat(progress.marksFor(date)).containsExactly(1, 3)
        }
}

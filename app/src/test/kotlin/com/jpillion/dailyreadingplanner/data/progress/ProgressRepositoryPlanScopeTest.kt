package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.plan.PlanRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * SB-T5 / SB-T6 (D-ALT-12/14/15): per-plan scoping of the progress store + the end-to-end
 * "no perceptible migration for the default user" assertion.
 *
 * - Two-plan ISOLATION: marks under one plan are invisible to another (a dropped `plan_id` filter
 *   would bleed plans together — mutation-killed).
 * - `hasAnyMarks()` stays GLOBAL (D-ALT-15): any mark in any plan makes it true.
 * - The end-to-end SB-T6: a migrated v1 DB, read through the NEW repo with the default plan, returns
 *   exactly what a Bible-Companion user had before the migration — they perceive nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressRepositoryPlanScopeTest {
    private val mcheyne = "mcheyne"
    private val bc = PlanRegistry.DEFAULT_PLAN_ID
    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)

    private lateinit var database: ProgressDatabase
    private lateinit var repository: ProgressRepositoryImpl

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ProgressDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = ProgressRepositoryImpl(database.readingProgressDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `marks under one plan are invisible to another plan`() =
        runTest {
            val date = LocalDate.of(2026, 6, 10)
            repository.setWholeDay(date, listOf(1, 2, 3), isRead = true, planId = bc)
            repository.setRead(date, 3, isRead = true, planId = mcheyne)

            // Bible Companion sees all three; M'Cheyne sees only the one it marked.
            assertThat(repository.streamsRead(date, planId = bc).first())
                .containsExactly(1, 2, 3)
            assertThat(repository.streamsRead(date, planId = mcheyne).first())
                .containsExactly(3)
        }

    @Test
    fun `counts strips and clearYear are all scoped per plan`() =
        runTest {
            val start = LocalDate.of(2026, 1, 1)
            val end = LocalDate.of(2026, 12, 31)
            val d1 = LocalDate.of(2026, 1, 1)
            val d2 = LocalDate.of(2026, 6, 10)
            repository.setWholeDay(d1, listOf(1, 2, 3), isRead = true, planId = bc)
            repository.setRead(d2, 1, isRead = true, planId = bc)
            repository.setRead(d1, 2, isRead = true, planId = mcheyne)

            // readCounts: BC has two marked days (3 + 1); M'Cheyne one (1).
            assertThat(repository.readCounts(start, end, planId = bc).first())
                .containsExactly(d1, 3, d2, 1)
            assertThat(repository.readCounts(start, end, planId = mcheyne).first())
                .containsExactly(d1, 1)

            // streamMarks scoped per plan.
            assertThat(repository.streamMarks(start, end, planId = mcheyne).first()[2])
                .containsExactly(d1)
            assertThat(repository.streamMarks(start, end, planId = bc).first()[2])
                .containsExactly(d1)

            // clearYear on BC leaves M'Cheyne untouched.
            repository.clearYear(2026, planId = bc)
            assertThat(repository.readCounts(start, end, planId = bc).first()).isEmpty()
            assertThat(repository.readCounts(start, end, planId = mcheyne).first()).containsExactly(d1, 1)
        }

    @Test
    fun `hasAnyMarks is GLOBAL - true for a mark under any plan`() =
        runTest {
            assertThat(repository.hasAnyMarks()).isFalse()
            // A mark under a NON-default plan still makes hasAnyMarks true (D-ALT-15).
            repository.setRead(LocalDate.of(2026, 6, 1), 3, isRead = true, planId = mcheyne)
            assertThat(repository.hasAnyMarks()).isTrue()
        }

    @Test
    fun `the default planId argument reads the flagship plan - parity`() =
        runTest {
            val date = LocalDate.of(2026, 3, 15)
            // Write with the explicit default; read with NO planId arg → must see the same marks.
            repository.setWholeDay(date, listOf(1, 2, 3), isRead = true, planId = bc)
            assertThat(repository.streamsRead(date).first()).containsExactly(1, 2, 3)
            // A write with NO planId arg lands in the flagship partition too.
            val other = LocalDate.of(2026, 3, 16)
            repository.setRead(other, 1, isRead = true)
            assertThat(repository.streamsRead(other, planId = bc).first()).containsExactly(1)
        }
}

/**
 * SB-T6 (D-ALT-14): a v1 DB with real history, migrated to v2 and read through the NEW
 * ProgressRepository with the active plan defaulted to bible_companion, returns IDENTICAL
 * streams-read / counts to what the same history meant before the migration. The upgrading Bible
 * Companion user perceives no migration (U-ALT-1, FR-ALT-2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressMigrationNoPerceptibleChangeTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            ProgressDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `migrated v1 history reads back identically through the new default-plan repository`() =
        runTest {
            val dbName = "perceptible-test.db"
            val day1 = LocalDate.of(2026, 1, 1) // whole day
            val day2 = LocalDate.of(2026, 6, 10) // partial (NT only)

            // 1. Seed a v1 DB exactly as a Bible Companion user would have it pre-Sprint-B.
            helper.createDatabase(dbName, 1).use { v1 ->
                listOf(1, 2, 3).forEach { s ->
                    v1.execSQL(
                        "INSERT INTO reading_progress (dateEpochDay, stream, readAtEpochMillis) VALUES (?, ?, ?)",
                        arrayOf<Any>(day1.toEpochDay(), s, 1_700_000_000_000 + s),
                    )
                }
                v1.execSQL(
                    "INSERT INTO reading_progress (dateEpochDay, stream, readAtEpochMillis) VALUES (?, ?, ?)",
                    arrayOf<Any>(day2.toEpochDay(), 3, 1_700_000_006_010),
                )
            }

            // 2. Migrate to v2.
            helper.runMigrationsAndValidate(dbName, 2, true, ProgressMigrations.MIGRATION_1_2).close()

            // 3. Open the SAME on-disk DB through the real Room DB + the NEW repository, default plan.
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val db =
                Room
                    .databaseBuilder(context, ProgressDatabase::class.java, dbName)
                    .allowMainThreadQueries()
                    .addMigrations(ProgressMigrations.MIGRATION_1_2)
                    .build()
            val repo = ProgressRepositoryImpl(db.readingProgressDao(), clock)

            // 4. The default-plan read returns EXACTLY the pre-migration history.
            assertThat(repo.streamsRead(day1).first()).containsExactly(1, 2, 3)
            assertThat(repo.streamsRead(day2).first()).containsExactly(3)
            assertThat(repo.readCounts(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).first())
                .containsExactly(day1, 3, day2, 1)
            assertThat(repo.allReadCounts().first()).containsExactly(day1, 3, day2, 1)
            assertThat(repo.hasAnyMarks()).isTrue()
            db.close()
            context.deleteDatabase(dbName)
        }
}

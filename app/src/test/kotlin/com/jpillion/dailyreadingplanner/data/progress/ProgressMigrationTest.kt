package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * THE Alt-Sprint-B hard gate (D-ALT-14, the worst-case guard): the `MIGRATION_1_2` 1 → 2 migration
 * preserves EVERY existing mark with `plan_id = "bible_companion"` and ZERO loss. This is the only
 * place existing user reading history is rewritten; a botched migration's blast radius is
 * "everyone's progress," so this runs the REAL exported schemas via Room's [MigrationTestHelper]
 * (not a hand-typed DDL) and asserts row-count + every tuple + Room's own schema validation.
 *
 * The exported 1.json/2.json are made available to the Robolectric AssetManager by the
 * `copyRoomSchemasForUnitTest` build wiring (they are excluded from the packaged APK via
 * androidResources.ignoreAssetsPattern, so they never ship). Mutation-verified: dropping the row
 * copy, stamping a wrong plan id, or mangling a column makes this red.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            ProgressDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    /** A representative seed: multiple years, whole + partial days, a Feb-29-adjacent day. */
    private data class Mark(
        val dateEpochDay: Long,
        val stream: Int,
        val readAtEpochMillis: Long,
    )

    private val seed =
        listOf(
            // 2026-01-01 whole day (all 3 streams).
            Mark(epochDay(2026, 1, 1), 1, 1_700_000_000_001),
            Mark(epochDay(2026, 1, 1), 2, 1_700_000_000_002),
            Mark(epochDay(2026, 1, 1), 3, 1_700_000_000_003),
            // 2026-06-10 partial (one stream).
            Mark(epochDay(2026, 6, 10), 3, 1_700_000_006_010),
            // 2024-02-28 — Feb-29-adjacent in a leap year (whole day).
            Mark(epochDay(2024, 2, 28), 1, 1_700_000_022_801),
            Mark(epochDay(2024, 2, 28), 2, 1_700_000_022_802),
            Mark(epochDay(2024, 2, 28), 3, 1_700_000_022_803),
            // A different year (year-isolation) — partial.
            Mark(epochDay(2027, 12, 31), 2, 1_700_000_123_102),
        )

    @Test
    fun `MIGRATION_1_2 preserves every row and stamps bible_companion with zero loss`() {
        // 1. Create the v1 DB and insert the v1-shape marks (no plan_id column).
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            seed.forEach { m ->
                v1.execSQL(
                    "INSERT INTO reading_progress (dateEpochDay, stream, readAtEpochMillis) VALUES (?, ?, ?)",
                    arrayOf<Any>(m.dateEpochDay, m.stream, m.readAtEpochMillis),
                )
            }
        }

        // 2. Run the real MIGRATION_1_2; Room validates the resulting v2 schema.
        val v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true, ProgressMigrations.MIGRATION_1_2)

        // 3a. Row count is identical — nothing dropped, nothing added.
        assertWithMessage("every v1 row must survive the migration")
            .that(queryCount(v2, "SELECT COUNT(*) FROM reading_progress"))
            .isEqualTo(seed.size)

        // 3b. Every row is stamped bible_companion — no other plan id, no NULL.
        assertWithMessage("every migrated row must carry plan_id = 'bible_companion'")
            .that(queryCount(v2, "SELECT COUNT(*) FROM reading_progress WHERE plan_id != 'bible_companion'"))
            .isEqualTo(0)

        // 3c. Every original (dateEpochDay, stream, readAtEpochMillis) tuple is preserved verbatim.
        v2
            .query(
                "SELECT plan_id, dateEpochDay, stream, readAtEpochMillis FROM reading_progress " +
                    "ORDER BY dateEpochDay, stream",
            ).use { cursor ->
                val migrated = mutableListOf<Pair<String, Mark>>()
                while (cursor.moveToNext()) {
                    migrated +=
                        cursor.getString(0) to
                        Mark(cursor.getLong(1), cursor.getInt(2), cursor.getLong(3))
                }
                val expected = seed.sortedWith(compareBy({ it.dateEpochDay }, { it.stream }))
                assertThat(migrated.map { it.second }).containsExactlyElementsIn(expected).inOrder()
                assertThat(migrated.map { it.first }.toSet()).containsExactly("bible_companion")
            }
        v2.close()
    }

    @Test
    fun `MIGRATION_1_2 on an empty v1 store yields an empty validated v2 store`() {
        helper.createDatabase(TEST_DB, 1).close()
        val v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true, ProgressMigrations.MIGRATION_1_2)
        assertThat(queryCount(v2, "SELECT COUNT(*) FROM reading_progress")).isEqualTo(0)
        v2.close()
    }

    private fun queryCount(
        db: SupportSQLiteDatabase,
        sql: String,
    ): Int = db.query(sql).use { if (it.moveToFirst()) it.getInt(0) else -1 }

    private companion object {
        const val TEST_DB = "progress-migration-test.db"

        fun epochDay(
            year: Int,
            month: Int,
            day: Int,
        ): Long =
            java.time.LocalDate
                .of(year, month, day)
                .toEpochDay()
    }
}

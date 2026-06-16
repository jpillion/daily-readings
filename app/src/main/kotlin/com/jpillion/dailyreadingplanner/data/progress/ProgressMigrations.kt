package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Progress-store schema migrations (ESpec-alt §5.2, D-ALT-13).
 *
 * THE high-risk migration of the alternate-schedules feature: the ONLY place existing user data is
 * rewritten. Zero loss is the bar (FR-ALT-2, U-ALT-1). `fallbackToDestructiveMigration` is NOT
 * enabled on [ProgressDatabase] (D-V3-15 — user data is never destroyed); a failed migration must
 * be a loud QA crash, never silent loss. The `MigrationTestHelper` zero-loss test + the
 * "no perceptible migration for the default user" test are the gate that keeps it off devices.
 */
object ProgressMigrations {
    /**
     * v1 → v2: add `plan_id` and make it part of the primary key. Every mark in the wild today is,
     * by definition, a Bible Companion mark (there is only one plan), so every row is stamped
     * [ReadingProgressEntity.DEFAULT_PLAN_ID] ("bible_companion"). That stamp is the *meaning* of
     * the existing data, so it is an explicit migration literal, NOT a schema-level column default
     * that could drift.
     *
     * The PK change forces the recreate-and-copy idiom (SQLite cannot alter a primary key in
     * place): create the v2 table, copy EVERY existing row exactly once stamping the flagship id,
     * drop the old table, rename. The new-table DDL is byte-identical to Room's generated v2 DDL so
     * Room's post-migration schema validation passes.
     */
    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. New table with the v2 schema. Must match Room's generated DDL for
                //    `reading_progress` at version 2 (validated by the migration test).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_progress_new` (" +
                        "`plan_id` TEXT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, " +
                        "`stream` INTEGER NOT NULL, " +
                        "`readAtEpochMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`plan_id`, `dateEpochDay`, `stream`))",
                )
                // 2. Copy EVERY existing row, stamping the flagship plan id. Zero loss — every
                //    (dateEpochDay, stream, readAtEpochMillis) tuple is preserved verbatim.
                db.execSQL(
                    "INSERT INTO `reading_progress_new` " +
                        "(`plan_id`, `dateEpochDay`, `stream`, `readAtEpochMillis`) " +
                        "SELECT '${ReadingProgressEntity.DEFAULT_PLAN_ID}', " +
                        "`dateEpochDay`, `stream`, `readAtEpochMillis` FROM `reading_progress`",
                )
                // 3. Replace the old table with the new one.
                db.execSQL("DROP TABLE `reading_progress`")
                db.execSQL("ALTER TABLE `reading_progress_new` RENAME TO `reading_progress`")
            }
        }
}

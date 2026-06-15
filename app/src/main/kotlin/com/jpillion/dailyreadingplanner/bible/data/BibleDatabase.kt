package com.jpillion.dailyreadingplanner.bible.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * VA-T6 / ESpec-v3 §4.2, D-V3-15 — the read-only KJV text database, opened from the bundled
 * `assets/bible/bible.db` via `createFromAsset` (wired in [com.jpillion.dailyreadingplanner.di.BibleModule]).
 *
 * Read-only by contract: `exportSchema = false` (an asset DB has no migration history to export —
 * the verification gate validates structure instead), and there are NO migrations — a shipped
 * text correction re-copies the asset (D-V3-8, [BibleAssetVersion]) rather than migrating.
 *
 * Coexists with the read-write `ProgressDatabase` but shares NO connection, DAO, or entity
 * (D-V3-15). Never holds user data — it is wiped on every content-version bump.
 */
@Database(entities = [VerseEntity::class], version = 1, exportSchema = false)
abstract class BibleDatabase : RoomDatabase() {
    abstract fun verseDao(): VerseDao
}

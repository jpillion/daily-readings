package com.jpillion.dailyreadingplanner.bible.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VE-T0 / ESpec-v3 §4.3, D-V3-8 — the deferred Sprint-A startup hook, now wired.
 *
 * `createFromAsset` copies the bundled `bible.db` exactly once; without this gate a shipped
 * text correction (a bumped [BibleAssetVersion.ASSET_CONTENT_VERSION]) would never reach an
 * existing install. [ensureUpToDate] compares the constant against the value persisted in the
 * DataStore [SettingsRepository] and, if the constant is newer, deletes the copied
 * `bible.db`/`-wal`/`-shm` from the database path BEFORE the Room builder opens it — forcing a
 * fresh re-copy — then persists the new version. Content-driven re-copy, NOT a Room migration.
 *
 * Called once, synchronously, from inside the `BibleDatabase` Hilt provider, BEFORE `.build()`.
 * That provider is only ever resolved off the main thread (the bible DB is first touched from a
 * suspend [RoomBibleTextSource.getVerses] on Room's background executor), so the [runBlocking]
 * DataStore read/write here never blocks the main thread — StrictMode-clean. The pure
 * compare/delete orchestration lives in [BibleAssetVersion]; this class supplies the Android
 * seams (database file path + the persistence read/write).
 *
 * The persisted version key lives in the existing DataStore alongside every other setting; this
 * is NOT user-writable data inside `bible.db` (the converse rule of D-V3-8 — bible.db is wiped
 * on every content bump, so user data stays in ProgressDatabase).
 */
@Singleton
class BibleAssetGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val assetVersionStore: BibleAssetVersionStore,
    ) {
        /**
         * If [BibleAssetVersion.ASSET_CONTENT_VERSION] is newer than the persisted version,
         * delete the copied DB files at [databaseName] so Room re-copies from the asset, then
         * persist the new version. Returns true if a re-copy was triggered. Idempotent: a
         * second call after the version is persisted is a no-op.
         */
        fun ensureUpToDate(databaseName: String): Boolean =
            runBlocking {
                val stored = assetVersionStore.read()
                BibleAssetVersion.ensureCurrent(
                    databaseFile = context.getDatabasePath(databaseName),
                    stored = stored,
                    persist = { newVersion -> runBlocking { assetVersionStore.write(newVersion) } },
                )
            }
    }

/**
 * Narrow seam over the persisted bible-asset content version (DataStore-backed in production,
 * trivially fakeable in tests). Kept separate from the full [SettingsRepository] interface so
 * the gate depends only on this one read/write pair.
 */
interface BibleAssetVersionStore {
    /** The persisted content version, or null if never written (fresh install). */
    suspend fun read(): Int?

    suspend fun write(version: Int)
}

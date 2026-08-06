package com.jpillion.dailyreadingplanner.bible.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D-OT-9 — the two anonymous ids FUMS requires alongside each `fumsToken`
 * (https://docs.api.bible/guides/fair-use/): a **device id** that persists across launches and a
 * **session id** regenerated per app session.
 *
 * Both are random UUIDs generated on this device. They are deliberately NOT derived from anything
 * identifying — no ANDROID_ID, no advertising id, no account. FUMS exists to tell publishers how
 * much scripture is being read, not who is reading it, and the app has never collected analytics
 * (the pre-V3 "no telemetry" decision stands — this is a licence obligation, not tracking).
 *
 * A narrow seam, mirroring [com.jpillion.dailyreadingplanner.bible.data.BibleAssetVersionStore],
 * so the reporter depends on this pair alone and tests never touch DataStore.
 */
interface FumsIdentity {
    /** Stable across launches; created on first use. */
    suspend fun deviceId(): String

    /** Regenerated per app session — one value for the lifetime of the process. */
    fun sessionId(): String
}

@Singleton
class DataStoreFumsIdentity
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : FumsIdentity {
        // Per-process, so a new launch is a new session. Not persisted, by definition.
        private val session: String = UUID.randomUUID().toString()

        override suspend fun deviceId(): String {
            dataStore.data.first()[KEY]?.let { return it }
            val generated = UUID.randomUUID().toString()
            // Re-read inside edit: two concurrent first-calls must not each write a different id.
            var stored = generated
            dataStore.edit { prefs ->
                val existing = prefs[KEY]
                if (existing != null) {
                    stored = existing
                } else {
                    prefs[KEY] = generated
                }
            }
            return stored
        }

        override fun sessionId(): String = session

        private companion object {
            val KEY = stringPreferencesKey("fums_device_id")
        }
    }

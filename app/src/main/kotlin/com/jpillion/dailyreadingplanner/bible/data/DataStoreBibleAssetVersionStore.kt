package com.jpillion.dailyreadingplanner.bible.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * VE-T0 — DataStore-backed [BibleAssetVersionStore]. Stores the bible-asset content version
 * under its own key in the existing shared preferences DataStore (the same store every other
 * setting uses; NOT inside the read-only bible.db, per the D-V3-8 converse rule). Absent key =
 * null = fresh install ⇒ first launch copies the asset and records the current version.
 */
class DataStoreBibleAssetVersionStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : BibleAssetVersionStore {
        override suspend fun read(): Int? = dataStore.data.first()[KEY]

        override suspend fun write(version: Int) {
            dataStore.edit { it[KEY] = version }
        }

        private companion object {
            val KEY = intPreferencesKey("bible_asset_content_version")
        }
    }

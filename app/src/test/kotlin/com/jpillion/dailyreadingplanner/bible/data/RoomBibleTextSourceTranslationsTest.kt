package com.jpillion.dailyreadingplanner.bible.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D-N-1 — proves the reader's version label is SOURCED FROM THE ASSET, not a hardcoded "KJV"
 * literal: [RoomBibleTextSource.translations] reads the bundled asset's `translation` table through
 * the real Room support-SQLite handle (a raw read, NOT a mapped `@Entity`, so it never alters the
 * pinned Room schema / identity hash). Today the asset carries exactly one row — KJV / "King James
 * Version" — which is what the reader displays ("KJV") and speaks ("King James Version").
 *
 * Runs real SQLite under Robolectric, opening the SAME [BibleDatabase] via the SAME `createFromAsset`
 * builder used in production [com.jpillion.dailyreadingplanner.di.BibleModule] — separate from the
 * 5-test [BibleDatabaseRoomOpenTest] gate (that gate's count and content are unchanged).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomBibleTextSourceTranslationsTest {
    private lateinit var db: BibleDatabase
    private lateinit var source: RoomBibleTextSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("bible.db")
        db =
            Room
                .databaseBuilder(context, BibleDatabase::class.java, "bible.db")
                .createFromAsset("bible/bible.db")
                .fallbackToDestructiveMigration(false)
                .build()
        source = RoomBibleTextSource(db.verseDao(), db)
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
        ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .deleteDatabase("bible.db")
    }

    @Test
    fun `translations reads the single KJV row from the bundled asset - code and name`() {
        val translations = runBlocking { source.translations() }
        assertThat(translations).hasSize(1)
        val kjv = translations.single()
        // The compact label the reader shows inline (D-N-2)...
        assertThat(kjv.code).isEqualTo("KJV")
        // ...and the unabbreviated name TalkBack speaks (D-N-2).
        assertThat(kjv.name).isEqualTo("King James Version")
    }
}

package com.jpillion.dailyreadingplanner.bible.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

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
        source = RoomBibleTextSource(db.verseDao(), db, Dispatchers.IO)
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

    /**
     * p1-04 — THE measurement that the `@IoDispatcher` seam is really the one doing the work.
     *
     * The existing test above passes identically whether the body says `withContext(ioDispatcher)`
     * or `withContext(Dispatchers.IO)`: it only checks the rows that come back. So it is blind to
     * a regression that re-hardcodes the platform dispatcher — which on Kotlin/Native does not
     * compile at all, and would surface as a phase-boundary failure months from now.
     *
     * Injecting a uniquely-named single thread makes the seam observable: the query runs on that
     * thread, and on no other. Hardcode the dispatcher again and this goes red immediately.
     */
    @Test
    fun `translations runs on the injected dispatcher and not on a hardcoded one`() {
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, PROBE_THREAD) }
        val probe = RecordingDispatcher(executor.asCoroutineDispatcher())
        try {
            val translations = runBlocking { RoomBibleTextSource(db.verseDao(), db, probe).translations() }

            // The rows still come back...
            assertThat(translations).hasSize(1)
            // ...and the injected dispatcher is what carried the read. Zero dispatches means the
            // production body named a dispatcher of its own and ignored the seam.
            assertThat(probe.dispatches > 0).isEqualTo(true)
            // ...onto the one thread this dispatcher owns, so the read really did leave the caller.
            assertThat(probe.threads).isEqualTo(setOf(PROBE_THREAD))
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * Records every block the source hands to its injected dispatcher, and the thread each one
     * actually ran on. Both halves matter: the count catches "the seam was bypassed", the thread
     * set catches "the seam was used but ran the work inline on the caller".
     */
    private class RecordingDispatcher(
        private val delegate: CoroutineDispatcher,
    ) : CoroutineDispatcher() {
        var dispatches = 0
            private set
        val threads = mutableSetOf<String>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            dispatches++
            delegate.dispatch(context) {
                synchronized(threads) { threads += Thread.currentThread().name }
                block.run()
            }
        }
    }

    private companion object {
        const val PROBE_THREAD = "p1-04-io-probe"
    }
}

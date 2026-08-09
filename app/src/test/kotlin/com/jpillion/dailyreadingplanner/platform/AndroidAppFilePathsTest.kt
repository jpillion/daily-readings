package com.jpillion.dailyreadingplanner.platform

import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * p1-04 — pins [AndroidAppFilePaths] as a **transcription**, not a redesign.
 *
 * The whole safety argument for introducing this seam inside a shipping Android release is that
 * it resolves to the exact directories the code already used. So each value is asserted against
 * the platform expression it replaced, not against a hardcoded string: a test that said
 * `"/data/data/…/cache"` would pass just as happily against a seam that had quietly relocated
 * user data, which is the failure this is here to prevent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAppFilePathsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val paths: AppFilePaths = AndroidAppFilePaths(context)

    @Test
    fun `databases is the directory Room resolves a database name against`() {
        assertThat(paths.databases).isEqualTo(context.getDatabasePath("bible.db").parentFile!!.toOkioPath())
    }

    @Test
    fun `a database file under databases is byte-for-byte the old getDatabasePath answer`() {
        assertThat(paths.databases / "progress.db")
            .isEqualTo(context.getDatabasePath("progress.db").toOkioPath())
        assertThat(paths.databases / "bible.db")
            .isEqualTo(context.getDatabasePath("bible.db").toOkioPath())
    }

    @Test
    fun `cache is the same directory FileBibleTextCache was handed`() {
        assertThat(paths.cache).isEqualTo(context.cacheDir.toOkioPath())
    }

    @Test
    fun `files is the app's private files directory`() {
        assertThat(paths.files).isEqualTo(context.filesDir.toOkioPath())
    }

    @Test
    fun `every directory exists when returned - the interface promises it`() {
        val fs = FileSystem.SYSTEM
        assertThat(fs.exists(paths.databases)).isTrue()
        assertThat(fs.exists(paths.cache)).isTrue()
        assertThat(fs.exists(paths.files)).isTrue()
    }

    @Test
    fun `the three directories are distinct - a cache eviction must never reach a database`() {
        assertThat(paths.databases == paths.cache).isEqualTo(false)
        assertThat(paths.databases == paths.files).isEqualTo(false)
        assertThat(paths.cache == paths.files).isEqualTo(false)
    }
}

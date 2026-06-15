package com.jpillion.dailyreadingplanner.bible.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** VA-T7 — pins the re-copy compare logic (D-V3-8). Mutations: flipping the comparison or
 *  skipping the delete must be killed. */
class BibleAssetVersionTest {
    @Test
    fun `newer constant triggers recopy`() {
        assertThat(BibleAssetVersion.shouldRecopy(constant = 2, stored = 1)).isTrue()
    }

    @Test
    fun `equal constant is a no-op`() {
        assertThat(BibleAssetVersion.shouldRecopy(constant = 1, stored = 1)).isFalse()
    }

    @Test
    fun `older constant never recopies`() {
        assertThat(BibleAssetVersion.shouldRecopy(constant = 1, stored = 2)).isFalse()
    }

    @Test
    fun `never-stored value forces an initial copy`() {
        assertThat(BibleAssetVersion.shouldRecopy(constant = 1, stored = null)).isTrue()
    }

    @Test
    fun `ensureCurrent deletes files and persists when newer`() {
        val deleted = mutableListOf<File>()
        var persisted: Int? = null
        val recopied =
            BibleAssetVersion.ensureCurrent(
                databaseFile = File("/tmp/bible.db"),
                stored = 1,
                constant = 2,
                deleteFiles = { deleted += it },
                persist = { persisted = it },
            )
        assertThat(recopied).isTrue()
        assertThat(deleted).containsExactly(File("/tmp/bible.db"))
        assertThat(persisted).isEqualTo(2)
    }

    @Test
    fun `ensureCurrent does nothing when current`() {
        var touched = false
        val recopied =
            BibleAssetVersion.ensureCurrent(
                databaseFile = File("/tmp/bible.db"),
                stored = 5,
                constant = 5,
                deleteFiles = { touched = true },
                persist = { touched = true },
            )
        assertThat(recopied).isFalse()
        assertThat(touched).isFalse()
    }

    @Test
    fun `starting asset content version is 1`() {
        assertThat(BibleAssetVersion.ASSET_CONTENT_VERSION).isEqualTo(1)
    }
}

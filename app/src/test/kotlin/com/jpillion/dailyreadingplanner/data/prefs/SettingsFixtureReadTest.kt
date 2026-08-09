package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import com.jpillion.dailyreadingplanner.bible.data.BibleAssetVersion
import com.jpillion.dailyreadingplanner.bible.data.DataStoreBibleAssetVersionStore
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleVersion
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.ResolveReadingDestinationPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveTrackingStartPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveUpgradeNoteUseCase
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.platform.SystemDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

/**
 * **PG-2** (task brief `p1-08`, ADR-0008). Reads a `settings.preferences_pb` **captured off a device
 * running the shipped 1.8.1 build** through the REAL [SettingsRepositoryImpl],
 * [PartialReadingRepositoryImpl] and [DataStoreBibleAssetVersionStore], and asserts every key and
 * value survives.
 *
 * **This is the silent half, and therefore the dangerous half.** A renamed DataStore key does not
 * crash — it silently resets the user. Theme reverts. The tracking-start date is lost.
 * `selected_plan` reverts to Bible Companion, so a M'Cheyne reader's progress *appears* to vanish
 * though every row is still in the database. And the first-run dialogs re-fire at a user who has
 * been using the app for a year.
 *
 * The test therefore asserts on two levels:
 *  1. **the literal key strings**, read raw out of the store — the same literal-pinning discipline
 *     this project applies to plan data, so a rename fails here and nowhere later; and
 *  2. **the value each production reader returns**, which is what a rename would silently degrade
 *     to a default.
 *
 * Every expected value is a **non-default** (see `fixtures/1.8.1/README.md` §5): a fixture whose
 * values all happened to equal the defaults would read back identically after a total reset and
 * would prove nothing.
 *
 * **Before the owner's capture lands** this test SKIPS, gated on `status` in
 * `fixtures/1.8.1/fixtures.properties`. Once that flips to `CAPTURED` a missing or unreadable
 * fixture is a HARD FAILURE — it can never silently pass. See that directory's README §10.
 */
class SettingsFixtureReadTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixtureTest(block: suspend Fixtures.() -> Unit) {
        val properties = loadProperties()
        val status = properties.getProperty(STATUS_KEY)?.trim()
        // Only the two known values are tolerated. A blank or misspelled status must NOT skip:
        // a gate that quietly disables itself on a typo is not a gate. (Found by running the
        // fail-demonstration in the fixture README §10 — the first draft skipped on a blank.)
        check(status == STATUS_PENDING || status == STATUS_CAPTURED) {
            "$DIR/$PROPERTIES_FILE has $STATUS_KEY='$status'; expected '$STATUS_PENDING' or '$STATUS_CAPTURED'"
        }
        Assume.assumeTrue(
            "PG-2 is waiting on the owner's 1.8.1 device capture " +
                "($DIR/$PROPERTIES_FILE says $STATUS_KEY=$status). See $DIR/README.md.",
            status == STATUS_CAPTURED,
        )

        // From here on the fixture MUST be present and well-formed. Never skip, always fail.
        val bytes =
            checkNotNull(resourceOrNull(STORE_FILE)) {
                "$DIR/$PROPERTIES_FILE says $STATUS_KEY=$STATUS_CAPTURED but $DIR/$STORE_FILE is " +
                    "missing. A captured fixture set must be complete; see $DIR/README.md."
            }
        require(bytes.isNotEmpty()) { "$DIR/$STORE_FILE is empty — an empty store proves nothing" }

        runTest {
            // Work on a copy: DataStore opens the file read-write, and the committed fixture must
            // never be mutated by running the tests.
            val file = File(tmp.newFolder(), STORE_FILE).apply { writeBytes(bytes) }
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
            try {
                Fixtures(properties, dataStore, SettingsRepositoryImpl(dataStore)).block()
            } finally {
                scope.cancel()
            }
        }
    }

    private class Fixtures(
        val expected: Properties,
        val dataStore: DataStore<Preferences>,
        val settings: SettingsRepositoryImpl,
    ) {
        fun expect(key: String): String =
            checkNotNull(expected.getProperty("settings.$key")?.trim()?.takeIf { it.isNotEmpty() }) {
                "$DIR/$PROPERTIES_FILE is missing a recorded value for 'settings.$key'"
            }
    }

    @Test
    fun `the fixture contains exactly the DataStore keys this build writes - by literal name`() =
        fixtureTest {
            val names =
                dataStore.data
                    .first()
                    .asMap()
                    .keys
                    .map { it.name }
            assertThat(
                names,
                name =
                    "the literal key strings are the contract. A rename here silently resets every " +
                        "shipped user, so this list is pinned exactly.",
            ).containsExactlyInAnyOrder(*(EXPECTED_KEYS).toTypedArray())
            assertThat(
                names,
                name =
                    "upgrade_note_shown must be ABSENT: it and reading_destination_prompt_completed " +
                        "are mutually exclusive by design (ResolveUpgradeNoteUseCase), so a fresh " +
                        "install can never produce both",
            ).doesNotContain("upgrade_note_shown")
        }

    @Test
    fun `every setting reads back the non-default value the owner stored`() =
        fixtureTest {
            assertThat(settings.themeMode.first(), name = "theme_mode")
                .isEqualTo(ThemeMode.valueOf(expect("theme_mode")))
            assertThat(settings.fontScale.first(), name = "font_scale").isEqualTo(expect("font_scale").toFloat())
            assertThat(settings.trackingStartDate.first(), name = "tracking_start_epoch_day")
                .isEqualTo(LocalDate.parse(expect("tracking_start_epoch_day.iso")))
            assertThat(settings.trackingStartInitialized.first(), name = "tracking_start_initialized")
                .isEqualTo(expect("tracking_start_initialized").toBoolean())
            assertThat(settings.reminderEnabled.first(), name = "reminder_enabled")
                .isEqualTo(expect("reminder_enabled").toBoolean())
            assertThat(settings.reminderTime.first(), name = "reminder_minute_of_day")
                .isEqualTo(LocalTime.fromSecondOfDay(expect("reminder_minute_of_day").toInt() * 60))
            assertThat(settings.persistentNotificationEnabled.first(), name = "persistent_notification_enabled")
                .isEqualTo(expect("persistent_notification_enabled").toBoolean())
            assertThat(settings.readingDestinationMode.first(), name = "reading_destination_mode")
                .isEqualTo(ReadingDestinationMode.valueOf(expect("reading_destination_mode")))
            assertThat(settings.externalBibleApp.first(), name = "bible_provider")
                .isEqualTo(ExternalBibleApp.valueOf(expect("bible_provider")))
            assertThat(settings.selectedBibleVersion.first(), name = "selected_bible_version")
                .isEqualTo(BibleVersion.valueOf(expect("selected_bible_version")))
            assertThat(settings.showStreaks.first(), name = "show_streaks")
                .isEqualTo(expect("show_streaks").toBoolean())
            assertThat(settings.selectedPlanId.first(), name = "selected_plan").isEqualTo(expect("selected_plan"))
            assertThat(
                settings.readingDestinationPromptCompleted.first(),
                name = "reading_destination_prompt_completed",
            ).isEqualTo(expect("reading_destination_prompt_completed").toBoolean())
        }

    /**
     * The recorded values must actually differ from the code defaults, or the fixture proves
     * nothing: a build that lost every key would read back exactly the same answers. Three of
     * these are traps where the intuitive "non-default" is in fact the current default —
     * `persistent_notification_enabled` defaults to **true**, `show_streaks` to **false**, and
     * `reading_destination_mode` to **EXTERNAL**.
     */
    @Test
    fun `no recorded value equals its code default`() =
        fixtureTest {
            assertThat(ThemeMode.valueOf(expect("theme_mode"))).isNotEqualTo(ThemeMode.SYSTEM)
            assertThat(expect("font_scale").toFloat()).isNotEqualTo(SettingsRepository.DEFAULT_FONT_SCALE)
            assertThat(expect("tracking_start_initialized").toBoolean()).isNotEqualTo(false)
            assertThat(expect("reminder_enabled").toBoolean()).isNotEqualTo(false)
            assertThat(LocalTime.fromSecondOfDay(expect("reminder_minute_of_day").toInt() * 60))
                .isNotEqualTo(SettingsRepository.DEFAULT_REMINDER_TIME)
            assertThat(expect("persistent_notification_enabled").toBoolean()).isNotEqualTo(true)
            assertThat(ReadingDestinationMode.valueOf(expect("reading_destination_mode")))
                .isNotEqualTo(ReadingDestinationMode.DEFAULT)
            assertThat(ExternalBibleApp.valueOf(expect("bible_provider"))).isNotEqualTo(ExternalBibleApp.DEFAULT)
            assertThat(BibleVersion.valueOf(expect("selected_bible_version"))).isNotEqualTo(BibleVersion.DEFAULT)
            assertThat(expect("show_streaks").toBoolean()).isNotEqualTo(false)
            assertThat(expect("selected_plan")).isNotEqualTo("bible_companion")
            assertThat(expect("reading_destination_prompt_completed").toBoolean()).isNotEqualTo(false)
        }

    @Test
    fun `the partial-segment cache and the bible asset version survive`() =
        fixtureTest {
            val partials =
                PartialReadingRepositoryImpl(dataStore, SystemDateProvider()).partialSegments.first()
            assertThat(partials, name = "partial_reading_segments token count")
                .hasSize(expect("partial_reading_segments.count").toInt())
            assertThat(
                partials.filter {
                    PartialSegmentToken.parse(it) == null
                },
                name = "every stored token must still decode with the shipped codec",
            ).isEmpty()

            assertThat(DataStoreBibleAssetVersionStore(dataStore).read(), name = "bible_asset_content_version")
                .isEqualTo(expect("bible_asset_content_version").toInt())
            assertThat(
                DataStoreBibleAssetVersionStore(dataStore).read(),
                name =
                    "a stored version equal to the build constant means the ported build must NOT " +
                        "re-copy the bible asset (D-V3-8)",
            ).isEqualTo(BibleAssetVersion.ASSET_CONTENT_VERSION)
        }

    /**
     * The consequence that matters to a shipped user, asserted through the REAL production
     * predicates rather than through a restatement of them: none of the three first-run surfaces
     * may re-fire. The progress stub reports a device with existing history — the worst case, since
     * that is exactly the user who would be insulted by being asked as though newly installed.
     */
    @Test
    fun `no first-run dialog re-fires at a shipped user`() =
        fixtureTest {
            val progress = HistoryBearingProgressRepository

            assertThat(
                ResolveTrackingStartPromptUseCase(settings, progress).invoke(),
                name = "the tracking-start prompt must stay suppressed",
            ).isFalse()
            assertThat(
                ResolveReadingDestinationPromptUseCase(settings, progress).invoke(),
                name = "the reading-destination question must stay suppressed",
            ).isFalse()
            assertThat(
                ResolveUpgradeNoteUseCase(settings, progress).invoke(),
                name = "the one-time upgrade note must stay suppressed",
            ).isFalse()
        }

    /**
     * A device that has reading history. Every other member is [TODO] on purpose: all three
     * first-run gates are supposed to short-circuit on the stored settings markers alone, so a
     * future change that made one of them consult progress would fail loudly here instead of
     * quietly changing when a shipped user gets interrupted.
     */
    private object HistoryBearingProgressRepository : ProgressRepository {
        override suspend fun hasAnyMarks(): Boolean = true

        override fun streamsRead(
            date: LocalDate,
            planId: String,
        ): Flow<Set<Int>> = TODO("not used by the first-run gates")

        override fun readCounts(
            start: LocalDate,
            end: LocalDate,
            planId: String,
        ): Flow<Map<LocalDate, Int>> = TODO("not used by the first-run gates")

        override fun allReadCounts(planId: String): Flow<Map<LocalDate, Int>> = TODO("not used by the first-run gates")

        override fun streamCounts(
            start: LocalDate,
            end: LocalDate,
            planId: String,
        ): Flow<Map<Int, Int>> = TODO("not used by the first-run gates")

        override fun streamMarks(
            start: LocalDate,
            end: LocalDate,
            planId: String,
        ): Flow<Map<Int, Set<LocalDate>>> = TODO("not used by the first-run gates")

        override suspend fun setRead(
            date: LocalDate,
            streamNumber: Int,
            isRead: Boolean,
            planId: String,
        ): Unit = TODO("not used by the first-run gates")

        override suspend fun setWholeDay(
            date: LocalDate,
            streamNumbers: List<Int>,
            isRead: Boolean,
            planId: String,
        ): Unit = TODO("not used by the first-run gates")

        override suspend fun clearYear(
            year: Int,
            planId: String,
        ): Unit = TODO("not used by the first-run gates")
    }

    private companion object {
        const val DIR = "fixtures/1.8.1"
        const val PROPERTIES_FILE = "fixtures.properties"
        const val STORE_FILE = "settings.preferences_pb"
        const val STATUS_KEY = "status"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_CAPTURED = "CAPTURED"

        /**
         * Every DataStore key this build writes, as a LITERAL string. Sourced by reading
         * `SettingsRepositoryImpl`, `PartialReadingRepositoryImpl` and
         * `DataStoreBibleAssetVersionStore` — never copied from documentation. `upgrade_note_shown`
         * is deliberately not here; see the absence assertion above.
         */
        val EXPECTED_KEYS =
            listOf(
                "theme_mode",
                "font_scale",
                "tracking_start_epoch_day",
                "tracking_start_initialized",
                "reminder_enabled",
                "reminder_minute_of_day",
                "persistent_notification_enabled",
                "reading_destination_mode",
                "bible_provider",
                "selected_bible_version",
                "reading_destination_prompt_completed",
                "show_streaks",
                "selected_plan",
                "partial_reading_segments",
                "bible_asset_content_version",
            )

        fun resourceOrNull(name: String): ByteArray? =
            SettingsFixtureReadTest::class.java.classLoader
                ?.getResourceAsStream("$DIR/$name")
                ?.use { it.readBytes() }

        fun loadProperties(): Properties {
            val bytes =
                checkNotNull(resourceOrNull(PROPERTIES_FILE)) {
                    "$DIR/$PROPERTIES_FILE is missing — it is the fixture contract and is committed"
                }
            return Properties().apply { load(bytes.inputStream().reader()) }
        }
    }
}

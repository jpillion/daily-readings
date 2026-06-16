package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * S3-T7 + S13 + Sprint K (D-23-1): a tap resolves on the destination MODE first (in-app vs.
 * external), then — for external mode — builds the URL for the user's CHOSEN external app, read
 * at tap time. A Settings change applies to the very next tap; the defaults (EXTERNAL + BLB)
 * preserve the behavior shipped since Sprint 4.
 */
class OpenReferenceUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val useCase = OpenReferenceUseCase(settings, ProviderUrlBuilder())

    @Test
    fun `default destination keeps the shipped blb behavior - first chapter of the portion`() =
        runTest {
            val genesis = portion(1, "Genesis" to 1, "Genesis" to 2)
            assertThat(useCase(genesis))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/gen/1/"))
        }

    @Test
    fun `multi-book portion opens the first book on a single-chapter app`() =
        runTest {
            val johannine = portion(3, "2 John" to 1, "3 John" to 1)
            assertThat(useCase(johannine))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/2jo/1/"))
        }

    @Test
    fun `the chosen external app is read at tap time`() =
        runTest {
            val genesis = portion(1, "Genesis" to 1, "Genesis" to 2)
            settings.setExternalBibleApp(ExternalBibleApp.YOUVERSION)
            assertThat(useCase(genesis))
                .isEqualTo(ReadingDestination.Web("https://www.bible.com/bible/1/GEN.1.KJV"))
            settings.setExternalBibleApp(ExternalBibleApp.BIBLE_GATEWAY)
            assertThat(useCase(genesis))
                .isEqualTo(
                    ReadingDestination.Web(
                        "https://www.biblegateway.com/passage/?search=Genesis+1-2&version=KJV",
                    ),
                )
        }

    // --- S15 (D-S15-3): the installed-app provider resolves to an app destination ---

    @Test
    fun `mysword resolves to an app destination carrying the BLB fallback url`() =
        runTest {
            settings.setExternalBibleApp(ExternalBibleApp.MYSWORD)
            val genesis = portion(1, "Genesis" to 1, "Genesis" to 2)
            assertThat(useCase(genesis))
                .isEqualTo(
                    ReadingDestination.MySwordApp(
                        url = "https://mysword.info/b?r=1.1",
                        fallbackUrl = "https://www.blueletterbible.org/kjv/gen/1/",
                    ),
                )
        }

    @Test
    fun `mysword multi-book portion opens the first book - fallback agrees`() =
        runTest {
            settings.setExternalBibleApp(ExternalBibleApp.MYSWORD)
            val johannine = portion(3, "2 John" to 1, "3 John" to 1)
            assertThat(useCase(johannine))
                .isEqualTo(
                    ReadingDestination.MySwordApp(
                        url = "https://mysword.info/b?r=63.1",
                        fallbackUrl = "https://www.blueletterbible.org/kjv/2jo/1/",
                    ),
                )
        }

    @Test
    fun `in-app mode resolves to an InApp destination carrying the whole portion`() =
        runTest {
            // D-23-1: IN_APP mode returns ReadingDestination.InApp(portion) — NOT a URL; the
            // multi-book portion rides whole, and no URL is built.
            settings.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            val johannine = portion(3, "2 John" to 1, "3 John" to 1)
            assertThat(useCase(johannine)).isEqualTo(ReadingDestination.InApp(johannine))
        }

    @Test
    fun `MUTATION the mode is checked before the external app - in-app wins over a stored app`() =
        runTest {
            // Load-bearing rule: OpenReferenceUseCase branches on the mode FIRST. Even with a
            // concrete external app stored (MySword), IN_APP mode must resolve to InApp — a
            // mutation that builds the URL before checking the mode reddens here.
            settings.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            settings.setExternalBibleApp(ExternalBibleApp.MYSWORD)
            val genesis = portion(1, "Genesis" to 1)
            assertThat(useCase(genesis)).isEqualTo(ReadingDestination.InApp(genesis))
        }

    @Test
    fun `external mode resolves to the external app even when an in-app app slot is unused`() =
        runTest {
            // The converse: EXTERNAL mode never returns InApp regardless of any remembered choice.
            settings.setReadingDestinationMode(ReadingDestinationMode.EXTERNAL)
            settings.setExternalBibleApp(ExternalBibleApp.BLB)
            val genesis = portion(1, "Genesis" to 1)
            assertThat(useCase(genesis))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/gen/1/"))
        }

    @Test
    fun `the persisted choice is never rewritten by resolving a destination`() =
        runTest {
            // D-S15-3 pin: degradation happens at launch time in the UI layer; the stored
            // app stays MYSWORD so a reinstall restores the user's choice. Nothing is written.
            settings.setExternalBibleApp(ExternalBibleApp.MYSWORD)
            settings.externalBibleAppCalls.clear()
            settings.destinationModeCalls.clear()
            useCase(portion(1, "Genesis" to 1))
            assertThat(settings.storedExternalBibleApp.value).isEqualTo(ExternalBibleApp.MYSWORD)
            assertThat(settings.externalBibleAppCalls).isEmpty()
            assertThat(settings.destinationModeCalls).isEmpty()
        }
}

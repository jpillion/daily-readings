package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * S3-T7 + S13: a tap builds the URL for the user's CHOSEN provider, read at tap time —
 * a Settings change applies to the very next tap, and the default (BLB) preserves the
 * behavior shipped since Sprint 4.
 */
class OpenReferenceUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val useCase = OpenReferenceUseCase(settings, ProviderUrlBuilder())

    @Test
    fun `default provider keeps the shipped blb behavior - first chapter of the portion`() =
        runTest {
            val genesis = portion(Stream.LAW_AND_HISTORY, "Genesis" to 1, "Genesis" to 2)
            assertThat(useCase(genesis))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/gen/1/"))
        }

    @Test
    fun `multi-book portion opens the first book on a single-chapter provider`() =
        runTest {
            val johannine = portion(Stream.NEW_TESTAMENT, "2 John" to 1, "3 John" to 1)
            assertThat(useCase(johannine))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/2jo/1/"))
        }

    @Test
    fun `the chosen provider is read at tap time`() =
        runTest {
            val genesis = portion(Stream.LAW_AND_HISTORY, "Genesis" to 1, "Genesis" to 2)
            settings.setBibleProvider(BibleProvider.YOUVERSION)
            assertThat(useCase(genesis))
                .isEqualTo(ReadingDestination.Web("https://www.bible.com/bible/1/GEN.1.KJV"))
            settings.setBibleProvider(BibleProvider.BIBLE_GATEWAY)
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
            settings.setBibleProvider(BibleProvider.MYSWORD)
            val genesis = portion(Stream.LAW_AND_HISTORY, "Genesis" to 1, "Genesis" to 2)
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
            settings.setBibleProvider(BibleProvider.MYSWORD)
            val johannine = portion(Stream.NEW_TESTAMENT, "2 John" to 1, "3 John" to 1)
            assertThat(useCase(johannine))
                .isEqualTo(
                    ReadingDestination.MySwordApp(
                        url = "https://mysword.info/b?r=63.1",
                        fallbackUrl = "https://www.blueletterbible.org/kjv/2jo/1/",
                    ),
                )
        }

    @Test
    fun `the in-app provider resolves to an InApp destination carrying the whole portion`() =
        runTest {
            // VD-T5 (D-V3-18): IN_APP returns ReadingDestination.InApp(portion) — NOT a URL; the
            // multi-book portion rides whole, and no URL is built (the builder errors for IN_APP).
            settings.setBibleProvider(BibleProvider.IN_APP)
            val johannine = portion(Stream.NEW_TESTAMENT, "2 John" to 1, "3 John" to 1)
            assertThat(useCase(johannine)).isEqualTo(ReadingDestination.InApp(johannine))
        }

    @Test
    fun `the persisted choice is never rewritten by resolving a destination`() =
        runTest {
            // D-S15-3 pin: degradation happens at launch time in the UI layer; the stored
            // provider stays MYSWORD so a reinstall restores the user's choice.
            settings.setBibleProvider(BibleProvider.MYSWORD)
            useCase(portion(Stream.LAW_AND_HISTORY, "Genesis" to 1))
            assertThat(settings.storedBibleProvider.value).isEqualTo(BibleProvider.MYSWORD)
            assertThat(settings.bibleProviderCalls).containsExactly(BibleProvider.MYSWORD)
        }
}

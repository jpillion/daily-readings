package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * H6 (BACKLOG #5) — a verse tap resolves to an external destination at the exact (book, ch, verse)
 * for the user's CHOSEN provider, read at tap time. D-H-4: IN_APP has no external verse target, so
 * the tap falls back to BLB without ever rewriting the stored IN_APP choice.
 */
class OpenVerseUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val useCase = OpenVerseUseCase(settings, ProviderUrlBuilder())

    private val genesis = BookCatalog.requireByName("Genesis")
    private val psalms = BookCatalog.requireByName("Psalms")

    @Test
    fun `default provider opens the verse at blb`() =
        runTest {
            assertThat(useCase(Reference(genesis, 1), 1))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/gen/1/1/"))
        }

    @Test
    fun `youversion verse url is read at tap time`() =
        runTest {
            settings.setBibleProvider(BibleProvider.YOUVERSION)
            assertThat(useCase(Reference(psalms, 23), 1))
                .isEqualTo(ReadingDestination.Web("https://www.bible.com/bible/1/PSA.23.1.KJV"))
        }

    @Test
    fun `bible gateway verse url carries book chapter verse`() =
        runTest {
            settings.setBibleProvider(BibleProvider.BIBLE_GATEWAY)
            assertThat(useCase(Reference(genesis, 1), 1))
                .isEqualTo(
                    ReadingDestination.Web(
                        "https://www.biblegateway.com/passage/?search=Genesis+1%3A1&version=KJV",
                    ),
                )
        }

    @Test
    fun `mysword resolves to an app destination with a BLB verse fallback`() =
        runTest {
            settings.setBibleProvider(BibleProvider.MYSWORD)
            assertThat(useCase(Reference(psalms, 23), 1))
                .isEqualTo(
                    ReadingDestination.MySwordApp(
                        url = "https://mysword.info/b?r=19.23.1",
                        fallbackUrl = "https://www.blueletterbible.org/kjv/psa/23/1/",
                    ),
                )
        }

    @Test
    fun `IN_APP falls back to BLB for the verse tap-out and never rewrites the stored choice`() =
        runTest {
            settings.setBibleProvider(BibleProvider.IN_APP)
            assertThat(useCase(Reference(genesis, 1), 1))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/gen/1/1/"))
            // D-H-4: resolving a tap-out NEVER writes the provider — the only call is the test's
            // own setup, and the stored value is still IN_APP.
            assertThat(settings.storedBibleProvider.value).isEqualTo(BibleProvider.IN_APP)
            assertThat(settings.bibleProviderCalls).containsExactly(BibleProvider.IN_APP)
        }

    @Test
    fun `a superscription tap - verse 0 - clamps to verse 1`() =
        runTest {
            assertThat(useCase(Reference(psalms, 23), 0))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/psa/23/1/"))
        }
}

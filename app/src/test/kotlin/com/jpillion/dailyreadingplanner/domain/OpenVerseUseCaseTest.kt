package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * H6 (BACKLOG #5) + Sprint K (D-23-1) — a verse tap ALWAYS resolves to an EXTERNAL destination at
 * the exact (book, ch, verse) for the user's CHOSEN external app, read at tap time, INDEPENDENT of
 * the destination mode (the in-app reader is never a verse-tap target). In-app mode keeps the
 * remembered external app (default BLB) and never rewrites the stored choice.
 */
class OpenVerseUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val useCase = OpenVerseUseCase(settings, ProviderUrlBuilder())

    private val genesis = BookCatalog.requireByName("Genesis")
    private val psalms = BookCatalog.requireByName("Psalms")

    @Test
    fun `default external app opens the verse at blb`() =
        runTest {
            assertThat(useCase(Reference(genesis, 1), 1))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/gen/1/1/"))
        }

    @Test
    fun `youversion verse url is read at tap time`() =
        runTest {
            settings.setExternalBibleApp(ExternalBibleApp.YOUVERSION)
            assertThat(useCase(Reference(psalms, 23), 1))
                .isEqualTo(ReadingDestination.Web("https://www.bible.com/bible/1/PSA.23.1.KJV"))
        }

    @Test
    fun `bible gateway verse url carries book chapter verse`() =
        runTest {
            settings.setExternalBibleApp(ExternalBibleApp.BIBLE_GATEWAY)
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
            settings.setExternalBibleApp(ExternalBibleApp.MYSWORD)
            assertThat(useCase(Reference(psalms, 23), 1))
                .isEqualTo(
                    ReadingDestination.MySwordApp(
                        url = "https://mysword.info/b?r=19.23.1",
                        fallbackUrl = "https://www.blueletterbible.org/kjv/psa/23/1/",
                    ),
                )
        }

    @Test
    fun `MUTATION verse tap is always external - in-app mode still opens an external verse url`() =
        runTest {
            // Load-bearing rule (verse-tap-always-external): the verse resolution depends ONLY on
            // the external app and NEVER on the mode. With the mode IN_APP and the default app BLB,
            // the tap-out still opens BLB externally — never an InApp destination. A mutation that
            // reintroduces a mode branch (e.g. returning InApp, or short-circuiting) reddens here.
            settings.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            val result = useCase(Reference(genesis, 1), 1)
            assertThat(result)
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/gen/1/1/"))
        }

    @Test
    fun `in-app mode honours the remembered external app and never rewrites it`() =
        runTest {
            // The remembered external app (here YouVersion) is what a verse tap-out uses even while
            // the mode is in-app; nothing is written by resolving.
            settings.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            settings.setExternalBibleApp(ExternalBibleApp.YOUVERSION)
            settings.externalBibleAppCalls.clear()
            settings.destinationModeCalls.clear()
            assertThat(useCase(Reference(psalms, 23), 1))
                .isEqualTo(ReadingDestination.Web("https://www.bible.com/bible/1/PSA.23.1.KJV"))
            assertThat(settings.storedExternalBibleApp.value).isEqualTo(ExternalBibleApp.YOUVERSION)
            assertThat(settings.externalBibleAppCalls).isEmpty()
            assertThat(settings.destinationModeCalls).isEmpty()
        }

    @Test
    fun `a superscription tap - verse 0 - clamps to verse 1`() =
        runTest {
            assertThat(useCase(Reference(psalms, 23), 0))
                .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/psa/23/1/"))
        }
}

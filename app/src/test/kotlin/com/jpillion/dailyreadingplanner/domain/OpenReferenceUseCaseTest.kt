package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
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
            assertThat(useCase(genesis)).isEqualTo("https://www.blueletterbible.org/kjv/gen/1/")
        }

    @Test
    fun `multi-book portion opens the first book on a single-chapter provider`() =
        runTest {
            val johannine = portion(Stream.NEW_TESTAMENT, "2 John" to 1, "3 John" to 1)
            assertThat(useCase(johannine)).isEqualTo("https://www.blueletterbible.org/kjv/2jo/1/")
        }

    @Test
    fun `the chosen provider is read at tap time`() =
        runTest {
            val genesis = portion(Stream.LAW_AND_HISTORY, "Genesis" to 1, "Genesis" to 2)
            settings.setBibleProvider(BibleProvider.YOUVERSION)
            assertThat(useCase(genesis)).isEqualTo("https://www.bible.com/bible/1/GEN.1.KJV")
            settings.setBibleProvider(BibleProvider.BIBLE_GATEWAY)
            assertThat(useCase(genesis))
                .isEqualTo("https://www.biblegateway.com/passage/?search=Genesis+1-2&version=KJV")
        }
}

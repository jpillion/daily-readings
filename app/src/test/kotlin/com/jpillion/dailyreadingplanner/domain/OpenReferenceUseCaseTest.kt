package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.BlbUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.Stream
import org.junit.Test

/** S3-T7: a tap on a portion opens its FIRST chapter's BLB URL (PRD flow U3). */
class OpenReferenceUseCaseTest {
    private val useCase = OpenReferenceUseCase(BlbUrlBuilder())

    @Test
    fun `multi-chapter portion opens its first chapter`() {
        val genesis = portion(Stream.LAW_AND_HISTORY, "Genesis" to 1, "Genesis" to 2)
        assertThat(useCase(genesis)).isEqualTo("https://www.blueletterbible.org/kjv/gen/1/")
    }

    @Test
    fun `multi-book portion opens the first book`() {
        val johannine = portion(Stream.NEW_TESTAMENT, "2 John" to 1, "3 John" to 1)
        assertThat(useCase(johannine)).isEqualTo("https://www.blueletterbible.org/kjv/2jo/1/")
    }
}

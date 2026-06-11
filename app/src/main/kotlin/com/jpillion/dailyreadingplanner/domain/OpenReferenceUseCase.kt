package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.reference.BlbUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.Portion
import javax.inject.Inject

/**
 * Builds the BLB URL a tap should open — the URL only; the Custom-Tab launch side-effect
 * lives in the UI layer (ESpec §8). A multi-chapter portion opens its FIRST chapter.
 */
class OpenReferenceUseCase
    @Inject
    constructor(
        private val urlBuilder: BlbUrlBuilder,
    ) {
        operator fun invoke(portion: Portion): String = urlBuilder.build(portion.firstRef)
    }

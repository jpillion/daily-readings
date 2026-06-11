package com.jpillion.dailyreadingplanner.data.reference

import com.jpillion.dailyreadingplanner.domain.model.Reference
import javax.inject.Inject

/**
 * Builds the outbound Blue Letter Bible KJV chapter URL (ESpec §5.2/§8). Deliberately the
 * ONLY place the BLB URL scheme appears, so a scheme change is a one-file swap (risk R2).
 */
class BlbUrlBuilder
    @Inject
    constructor() {
        fun build(reference: Reference): String =
            "https://www.blueletterbible.org/kjv/${reference.book.blbAbbrev}/${reference.chapter}/"
    }

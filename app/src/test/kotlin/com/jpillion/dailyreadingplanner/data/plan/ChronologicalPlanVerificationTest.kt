package com.jpillion.dailyreadingplanner.data.plan

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanDto
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanJson
import com.jpillion.dailyreadingplanner.data.plan.dto.RegistryDto
import com.jpillion.dailyreadingplanner.data.plan.dto.RegistryJson
import org.junit.Test
import java.io.File

/**
 * CHR-3 — THE CHRONOLOGICAL GATE (alternate-schedules Sprint F, D-ALT-24).
 *
 * Proves the SHIPPED Chronological asset (assets/plans/chronological/plan.json, read via planAssetsDir,
 * the same mechanism that guards the exact APK file) is structurally correct against Blue Letter Bible's
 * SHA-pinned "Daily Bible Reading Program — Chronological Plan" (Nathan Gammie) — the owner-designated
 * single canonical source.
 *
 * THE RELAXATION (D-ALT-24, owner-signed, recorded in docs/data/README.md): unlike the Bible Companion
 * and M'Cheyne gates, this plan ships WITHOUT an independent second-source day-by-day fixture (no
 * genuinely independent second witness of this ordering exists — see the README "Chronological plan"
 * NO-GO analysis). In its place stands a RIGOROUS SINGLE-SOURCE STRUCTURAL GATE whose centrepiece is the
 * whole-Bible "every chapter exactly once" coverage invariant — the strongest single-source completeness
 * + correctness guarantee available for a zero-verse-split plan: a dropped, duplicated, or mistyped
 * chapter breaks it. Mirrors ReadingPlanVerificationTest / McheynePlanVerificationTest discipline.
 */
class ChronologicalPlanVerificationTest {
    private data class CatalogBook(
        val order: Int,
        val name: String,
        val chapterCount: Int,
        val blbAbbrev: String,
    )

    private val plansDir =
        File(System.getProperty("planAssetsDir") ?: error("planAssetsDir system property not set"))

    private val plan: PlanDto =
        PlanJson.decode(plansDir.resolve("plans/chronological/plan.json").readText())

    private val catalog: List<CatalogBook> =
        testResource("book_catalog.csv")
            .lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val (order, name, count, abbrev) = line.split(",")
                CatalogBook(order.toInt(), name, count.toInt(), abbrev)
            }
    private val byName = catalog.associateBy { it.name }

    private val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    private fun testResource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "test resource $name not found" }.readText()

    @Test
    fun `schema header declares a single-stream date-anchored 365-day chronological plan`() {
        assertThat(plan.schemaVersion).isEqualTo(3)
        assertThat(plan.planId).isEqualTo("chronological")
        assertThat(plan.anchoring).isEqualTo("DATE")
        assertThat(plan.dayCount).isEqualTo(365)
        assertThat(plan.source).isNotEmpty()
        // N=1: exactly one stream, numbered 1.
        assertThat(plan.streams.map { it.number }).containsExactly(1)
    }

    @Test
    fun `plan has exactly 365 days with correct per-month counts - no Feb 29 - every date unique`() {
        assertThat(plan.days).hasSize(365)
        for (m in 1..12) {
            assertWithMessage("month $m day count")
                .that(plan.days.count { it.month == m })
                .isEqualTo(daysPerMonth[m - 1])
        }
        assertWithMessage("no Feb 29 entry (D1)")
            .that(plan.days.none { it.month == 2 && it.day == 29 })
            .isTrue()
        val keys = plan.days.map { it.month to it.day }
        assertWithMessage("every (month, day) unique").that(keys.toSet()).hasSize(keys.size)
    }

    @Test
    fun `every day has exactly one portion on stream 1 with non-empty refs`() {
        for (day in plan.days) {
            assertWithMessage("portions on ${day.month}/${day.day}")
                .that(day.portions.map { it.stream })
                .containsExactly(1)
            assertWithMessage("refs on ${day.month}/${day.day}")
                .that(day.portions.single().refs)
                .isNotEmpty()
        }
    }

    @Test
    fun `no ref is verse-windowed - chronological is whole-chapters-only`() {
        // The anti-drift counterpart to M'Cheyne's "windows tile" pin: BLB's chronological plan has ZERO
        // verse splits (no colon notation anywhere in the source). Every ref is a whole chapter.
        val windowed =
            plan.days.flatMap { day ->
                day.portions.flatMap { p ->
                    p.refs
                        .filter { it.verseStart != null || it.verseEnd != null }
                        .map { "${day.month}/${day.day} ${it.book} ${it.chapter}" }
                }
            }
        assertWithMessage("chronological must carry NO verse windows").that(windowed).isEmpty()
    }

    @Test
    fun `every ref resolves in the catalog with chapter in range`() {
        for (day in plan.days) {
            for (ref in day.portions.single().refs) {
                val book = byName[ref.book]
                assertWithMessage("unknown book '${ref.book}' on ${day.month}/${day.day}")
                    .that(book)
                    .isNotNull()
                assertWithMessage("${ref.book} ${ref.chapter} out of range on ${day.month}/${day.day}")
                    .that(ref.chapter)
                    .isIn(1..book!!.chapterCount)
            }
        }
    }

    @Test
    fun `coverage invariant - every chapter of all 66 books is read EXACTLY ONCE`() {
        // THE single-source structural gate (D-ALT-24). Because the plan is whole-chapters-only, the
        // multiset of (book, chapter) across all 365 days must equal every chapter of all 66 books
        // exactly once: total == 1,189, each chapter read-count == 1, no gaps, no duplicates. This is
        // the strongest possible single-source proof of a complete, correct transcription.
        val reads = mutableMapOf<Pair<String, Int>, Int>()
        for (day in plan.days) {
            for (ref in day.portions.single().refs) {
                val key = ref.book to ref.chapter
                reads[key] = (reads[key] ?: 0) + 1
            }
        }

        val expectedTotal = catalog.sumOf { it.chapterCount }
        assertWithMessage("total chapter count across all 66 books").that(expectedTotal).isEqualTo(1189)
        assertWithMessage("total readings in the plan").that(reads.values.sum()).isEqualTo(1189)

        val errors = mutableListOf<String>()
        for (book in catalog) {
            for (ch in 1..book.chapterCount) {
                val got = reads[book.name to ch] ?: 0
                if (got != 1) errors += "${book.name} $ch read $got times, expected 1"
            }
        }
        // Also catch any (book, chapter) read that is NOT a real chapter of a real book (over-read).
        val catalogKeys = catalog.flatMap { b -> (1..b.chapterCount).map { b.name to it } }.toSet()
        for (key in reads.keys) {
            if (key !in catalogKeys) errors += "${key.first} ${key.second} is not a catalog chapter"
        }
        assertWithMessage("chapter-exactly-once coverage errors").that(errors).isEmpty()
    }

    @Test
    fun `endpoints are pinned - Day 1 is Genesis 1-3 and Day 365 is Revelation 19-22`() {
        // Day 1 = Jan 1; Day 365 = Dec 31 (365 contiguous date-anchored days, Feb=28).
        val jan1 = plan.days.single { it.month == 1 && it.day == 1 }
        assertThat(
            jan1.portions
                .single()
                .refs
                .map { it.book to it.chapter },
        ).containsExactly("Genesis" to 1, "Genesis" to 2, "Genesis" to 3)
            .inOrder()
        val dec31 = plan.days.single { it.month == 12 && it.day == 31 }
        assertThat(
            dec31.portions
                .single()
                .refs
                .map { it.book to it.chapter },
        ).containsExactly(
            "Revelation" to 19,
            "Revelation" to 20,
            "Revelation" to 21,
            "Revelation" to 22,
        ).inOrder()
    }

    @Test
    fun `the asset planId matches the registry id`() {
        // Anti-drift: the registry entry that selects this asset and the asset's self-declared planId
        // must agree (the loader enforces this at runtime; pin it at the gate too).
        val registry: RegistryDto =
            RegistryJson.decode(plansDir.resolve("plans/registry.json").readText())
        val entry = registry.plans.single { it.id == "chronological" }
        assertThat(entry.asset).isEqualTo("plans/chronological/plan.json")
        assertThat(plan.planId).isEqualTo(entry.id)
    }
}

package com.jpillion.dailyreadingplanner

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.matches
import assertk.assertions.startsWith
import org.junit.Test
import java.io.File

/**
 * Kotlin/Native rejects characters in backticked identifiers that the JVM accepts. Brief p1-09
 * found this the expensive way: `Name contains illegal characters` from the first real Apple
 * compile, against 120 test names that had been green on the JVM for months.
 *
 * **This gate is an ALLOWLIST, and that is the whole point.** The first finding was "the comma is
 * illegal"; the lesson is that we did not know the illegal set. Acting on that lesson is what
 * caught the rest: a deliberate probe compile of one name per observed character found that
 * **parentheses are rejected too** - another 42 names that had been carried as "reported OK", about
 * 40% more than was budgeted. A denylist would have encoded the comma and silently admitted every
 * paren.
 *
 * So [ALLOWED_CHARACTERS] contains only characters *proven by a native compile* to be legal.
 * As of the probe it is verified complete for this codebase: the only out-of-allowlist characters
 * anywhere in the suite are the comma and the two parens.
 *
 * **Adding a character is a deliberate act:** compile a probe containing it for a real Apple
 * target, then add it here with that evidence in the commit message. Never widen the allowlist to
 * make a red build green - that converts a caught defect into a shipped one.
 *
 * Scope: backticked `fun` names, which is where this repo's naming convention lives. Backticked
 * class and property names are not scanned; the convention does not use them.
 *
 * See `docs/test-port-strategy.md` section 12.
 */
class TestNamePortabilityGateTest {
    @Test
    fun `every backticked test name uses only characters proven to compile on every target`() {
        val violations =
            scanNames().mapNotNull { (file, name) ->
                val bad = name.filterNot { it in ALLOWED_CHARACTERS }.toSortedSet()
                when {
                    bad.isEmpty() -> null
                    else ->
                        "${file.name}: `$name`" +
                            bad.joinToString("") { c -> "\n        illegal character '$c' (U+%04X)".format(c.code) }
                }
            }

        assertThat(
            violations,
            name =
                "These names contain characters NOT proven to compile on Kotlin/Native.\n" +
                    "Rename them. Only if you have compiled a probe containing the character for a real\n" +
                    "Apple target may you add it to ALLOWED_CHARACTERS, with that evidence in the commit\n" +
                    "message. Never widen the allowlist just to make this green.\n\n" +
                    violations.joinToString("\n"),
        ).isEmpty()
    }

    /**
     * The vacuity guard. A gate that scans nothing passes forever, which is how a release check can
     * sit green over a broken build. This asserts the scan actually reached the real source tree.
     */
    @Test
    fun `the scan is not vacuous - it finds the real source roots and the whole suite`() {
        val roots = testSourceRoots()
        assertThat(roots, name = "no test source root found - the scan would pass vacuously")
            .isNotEmpty()
        assertThat(
            roots.map {
                it.invariantSeparatorsPath
            },
            name = "the Android unit-test source root must be among the scanned roots",
        ).contains(File(repoRoot(), "app/src/test/kotlin").invariantSeparatorsPath)

        val names = scanNames()
        assertThat(names.size, name = "found ${names.size} backticked names - far too few to be the real suite")
            .isGreaterThanOrEqualTo(MINIMUM_EXPECTED_NAMES)
    }

    /**
     * [MODULES] is an explicit list rather than a glob on purpose: a glob picks up the agent
     * worktrees under `.claude` - a full second copy of this repository - and the throwaway probes
     * under `spikes`, which would double-count the suite and fail on files this gate has no
     * business judging. The cost of being explicit is that a NEW module would go unscanned, so this
     * test closes that hole instead of leaving it open.
     *
     * Note for anyone editing these KDoc blocks: Kotlin block comments NEST, so writing a glob as
     * `spikes` followed by a slash and two stars opens a nested comment that is never closed and
     * breaks the whole file. That is not hypothetical - it happened while writing this gate.
     */
    @Test
    fun `no unlisted module hides an unscanned test source root`() {
        val unlisted =
            repoRoot()
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory && !it.name.startsWith(".") && it.name != "build" }
                .filter { it.name !in MODULES }
                .filter { module ->
                    File(module, "src")
                        .listFiles()
                        .orEmpty()
                        .any { it.isDirectory && TEST_SOURCE_SET.matches(it.name) && File(it, "kotlin").isDirectory }
                }.map { it.name }

        assertThat(
            unlisted,
            name =
                "These modules have test sources this gate does NOT scan. Add them to MODULES - in\n" +
                    "particular `shared`, the moment shared/src/commonTest/kotlin exists.\n" +
                    unlisted.joinToString("\n"),
        ).isEmpty()
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate the repository root above ${System.getProperty("user.dir")}")
    }

    private fun testSourceRoots(): List<File> =
        MODULES.flatMap { module ->
            File(repoRoot(), "$module/src")
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory && TEST_SOURCE_SET.matches(it.name) }
                .map { File(it, "kotlin") }
                .filter { it.isDirectory }
        }

    /**
     * Deliberately skips commented-out code and KDoc prose. A name inside a comment never reaches
     * the compiler, so failing on one would be a false positive - and that exact difference is what
     * produced two conflicting counts of the comma offenders before the probe reconciled them.
     */
    private fun scanNames(): List<Pair<File, String>> =
        testSourceRoots()
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .flatMap { file -> file.readLines().mapNotNull { line -> nameIn(line)?.let { file to it } } }

    /** The backticked name declared on [line], or null if the line declares none or is a comment. */
    private fun nameIn(line: String): String? {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) return null
        val match = BACKTICKED_FUN.find(line) ?: return null
        if (line.take(match.range.first).contains("//")) return null
        return match.groupValues[1]
    }

    private companion object {
        /**
         * Characters proven by a native probe compile to be legal in a backticked identifier on
         * every target we ship to. NOT a denylist of known-bad characters - see the class KDoc.
         *
         * Deliberately ABSENT: the comma and both parentheses, all three rejected by Kotlin/Native.
         * The em dash U+2014 IS present: the probe accepted it, so the 6 names using it need no
         * change.
         */
        val ALLOWED_CHARACTERS: Set<Char> =
            buildSet {
                addAll('a'..'z')
                addAll('A'..'Z')
                addAll('0'..'9')
                addAll(" -'_=+\u2014".toList())
            }

        /** Explicit, not a glob - see the `no unlisted module` test for why, and for the guard. */
        val MODULES = listOf("app", "shared")

        val TEST_SOURCE_SET = Regex(".*[Tt]est.*")
        val BACKTICKED_FUN = Regex("""\bfun\s+`([^`]+)`""")

        /** A floor, not a pin: catches "scanned nothing" without churning on every new test. */
        const val MINIMUM_EXPECTED_NAMES = 800
    }
}

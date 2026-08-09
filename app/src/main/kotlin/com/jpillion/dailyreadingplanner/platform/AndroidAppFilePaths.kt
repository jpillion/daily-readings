package com.jpillion.dailyreadingplanner.platform

import android.content.Context
import okio.Path
import okio.Path.Companion.toOkioPath

/**
 * The Android [AppFilePaths] (p1-04). Every value here is a **literal transcription** of the
 * location the code already used before the seam existed, so this change moves not one byte on a
 * shipped device:
 *
 * - [databases] — `context.getDatabasePath(name).parentFile`, i.e. the directory Room itself
 *   resolves a database name against. `BibleAssetGate` previously called
 *   `context.getDatabasePath("bible.db")` directly; `databases / "bible.db"` is the same file,
 *   because `getDatabasePath` is defined as that directory joined with the name.
 * - [cache] — `context.cacheDir`, which is what `FileBibleTextCache` was handed.
 * - [files] — `context.filesDir`. No caller uses it yet; it is on the interface because the
 *   third category exists on both platforms and leaving it out would invite the next caller to
 *   reach for a `Context` again.
 *
 * The interface promises the directories exist, so each getter creates it. On Android that is
 * effectively a no-op — `cacheDir`/`filesDir` are created by the framework and Room creates
 * `databases/` when it opens — but the promise is the seam's, not the platform's, and iOS will
 * need it for real.
 *
 * A `Context`-holding class rather than an object: the location answer is a device fact, not a
 * constant. iOS supplies its own actual, which resolves the **App Group** container rather than
 * the app sandbox (D-PORT-4), which is exactly why callers must not compute these themselves.
 */
class AndroidAppFilePaths(
    private val context: Context,
) : AppFilePaths {
    override val databases: Path
        get() =
            context
                .getDatabasePath(PROBE_DB_NAME)
                .parentFile!!
                .also { it.mkdirs() }
                .toOkioPath()

    override val cache: Path
        get() = context.cacheDir.also { it.mkdirs() }.toOkioPath()

    override val files: Path
        get() = context.filesDir.also { it.mkdirs() }.toOkioPath()

    private companion object {
        /**
         * Any name works: [Context.getDatabasePath] resolves `<databases dir>/<name>`, and only the
         * parent is read. Naming it after a database the app actually ships would suggest this
         * value is about that database, which it is not.
         */
        const val PROBE_DB_NAME = "any.db"
    }
}

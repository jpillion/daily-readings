package com.jpillion.dailyreadingplanner.platform

import okio.Path

/**
 * Where this app keeps its private data on this device.
 *
 * These are the app's own directories: not user-visible, not shared with other apps, backed up or
 * excluded from backup according to each platform's own convention for that category. Callers must
 * never construct a path by string-appending to a platform directory of their own; every private
 * file the app owns is resolved from here, so that the *location* decision has exactly one home.
 *
 * Directories are guaranteed to exist when returned. Implementations create them if needed.
 */
interface AppFilePaths {
    /**
     * The directory holding SQLite databases. A database's file is [databases] / "<name>",
     * with its `-wal` and `-shm` sidecars alongside.
     */
    val databases: Path

    /**
     * The directory for regenerable caches. The platform may reclaim this space at any time, so
     * nothing whose loss the user would notice belongs here.
     */
    val cache: Path

    /** The directory for small persistent app files that are not databases and not caches. */
    val files: Path
}

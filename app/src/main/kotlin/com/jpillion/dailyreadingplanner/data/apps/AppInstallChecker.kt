package com.jpillion.dailyreadingplanner.data.apps

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Install-detection seam (S15, D-S15-2 — the D-S12-6 pattern): the Settings screen gates
 * installed-app providers (MySword) on this, so the enable/disable UX is unit-testable with
 * a fake. The production check needs the matching manifest `<queries>` entry for the target
 * package — without it, API 30+ package visibility makes every lookup fail.
 */
interface AppInstallChecker {
    fun isInstalled(packageName: String): Boolean
}

class PackageManagerAppInstallChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppInstallChecker {
        override fun isInstalled(packageName: String): Boolean =
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
    }

package com.jpillion.dailyreadingplanner.ui.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination

private const val TAG = "CustomTabLauncher"

/**
 * Opens [url] in a Chrome Custom Tab (ESpec §8); falls back to a plain ACTION_VIEW if no
 * Custom-Tabs-capable browser exists, and no-ops (logged) on a browserless device — the
 * planner itself must keep working offline/browserless (G-OFFLINE).
 */
fun launchCustomTab(
    context: Context,
    url: String,
) {
    val uri = url.toUri()
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Log.w(TAG, "No browser available to open $url")
        }
    }
}

/**
 * Launches a resolved [ReadingDestination] (S15, D-S15-3): web destinations ride the Custom
 * Tab; MySword fires the vendor-documented explicit-component intent into the app, and if
 * the app has been uninstalled since the user chose it, the tap falls back to the BLB URL in
 * the browser — NEVER the mysword.info stub page. The persisted provider choice is not
 * touched here (reinstalling MySword restores it).
 */
fun launchReadingDestination(
    context: Context,
    destination: ReadingDestination,
) {
    when (destination) {
        is ReadingDestination.Web -> launchCustomTab(context, destination.url)
        is ReadingDestination.MySwordApp -> {
            val intent =
                Intent(Intent.ACTION_VIEW, destination.url.toUri())
                    .setClassName(
                        ReadingDestination.MYSWORD_PACKAGE,
                        ReadingDestination.MYSWORD_LINK_ACTIVITY,
                    )
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Log.w(TAG, "MySword not available; falling back to ${destination.fallbackUrl}")
                launchCustomTab(context, destination.fallbackUrl)
            }
        }
    }
}

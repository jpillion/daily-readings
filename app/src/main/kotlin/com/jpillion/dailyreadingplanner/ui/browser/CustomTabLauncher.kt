package com.jpillion.dailyreadingplanner.ui.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

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

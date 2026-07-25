package com.jpillion.dailyreadingplanner.bible.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

// Q2 — the platform side of copy. VerseClipboardFormatter builds the string (pure, in the
// ViewModel per D-Q-4); this file writes it to the system clipboard as a Route-level side effect,
// mirroring the existing openDestinationEvents -> launchReadingDestination idiom.
//
// The platform ClipboardManager is used deliberately in preference to Compose's
// LocalClipboardManager: its status has churned across Compose versions, while the platform API is
// stable, minSdk-safe and Robolectric-friendly.

/** The clip's user-visible label (shown by some system clipboard UIs); short and constant. */
private const val CLIP_LABEL = "Bible verses"

/**
 * Writes [text] to the system clipboard. A device with no clipboard service (never true in
 * practice, but the cast is nullable) is a silent no-op rather than a crash — a failed copy must
 * never take the reader down.
 */
fun copyVerseTextToClipboard(
    context: Context,
    text: String,
) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
}

/**
 * **D-Q-5** — whether the app should show its own "Copied" confirmation for [sdkInt].
 *
 * Android 13 (API 33, [Build.VERSION_CODES.TIRAMISU]) added a system-drawn clipboard confirmation
 * for every copy, so an app toast on top of it double-reports the same event. Below 33 nothing is
 * shown by the system, so the app must confirm itself.
 *
 * Pure and JVM-pinned (the SDK level is a parameter, never read from [Build] in here) so the rule
 * is testable at the boundary without a device or a Robolectric SDK swap.
 */
fun shouldShowCopyConfirmation(sdkInt: Int): Boolean = sdkInt < Build.VERSION_CODES.TIRAMISU

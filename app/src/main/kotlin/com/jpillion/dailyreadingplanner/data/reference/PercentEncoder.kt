package com.jpillion.dailyreadingplanner.data.reference

/**
 * p1-03 / ADR-0014 Amendment A1 — the percent-encoder [ProviderUrlBuilder] uses, written by hand
 * because of **where it lives**, not because encoding is hard.
 *
 * [ProviderUrlBuilder]'s port destination is `shared/domain` (port-inventory 3.7): it is pure URL
 * construction over the book catalog with no IO. ADR-0001's forbidden list for `shared/domain` is
 * `java.*, android.*, Room, DataStore, Compose, okio, Ktor` — so it can use neither
 * `java.net.URLEncoder` nor Ktor's `encodeURLParameter`. `bible/data/remote/HttpFumsReporter`, which
 * lands in `shared/data`, uses Ktor's encoder; that split is A1's whole point.
 *
 * ## This is form encoding, and that is deliberate
 *
 * It reproduces `java.net.URLEncoder.encode(value, "UTF-8")` — HTML form encoding
 * (`application/x-www-form-urlencoded`) — **byte for byte**, NOT RFC 3986 percent encoding. The
 * three differences that matter:
 *
 * | Input | This encoder / `URLEncoder` | A naive RFC 3986 encoder |
 * |---|---|---|
 * | space | `+` | `%20` |
 * | `~` | `%7E` | `~` (unreserved) |
 * | `*` | `*` | `%2A` |
 *
 * Reproducing the shipped bytes is the requirement, and the space rule is the one that bites:
 * [ProviderUrlBuilder] builds Bible Gateway searches that contain spaces (`?search=Genesis+1-2`),
 * and those URLs were **live-verified 134/134 against Bible Gateway and 132/132 against YouVersion**
 * (sprint 13), with the verse-level shapes verified again in sprint 00H
 * (`docs/data/provider-link-checks.md`). `ProviderUrlBuilderTest`'s expectations ARE that
 * specification. **Any diff in its output is a bug here, not a stale test.**
 *
 * The RFC-3986-correct behaviour is not "better" for this call site; it is a different, unverified
 * URL corpus. Changing it is a separate, live-re-verified change on Android first.
 *
 * ## Portability
 *
 * `String.encodeToByteArray()` is `kotlin.text`, so this compiles unchanged on Kotlin/Native. The
 * one behavioural corner it does not reproduce is an **unpaired surrogate**: `URLEncoder` emits
 * `%3F` (the charset encoder's `?` replacement) where this emits `%EF%BF%BD` (U+FFFD). Book names
 * and chapter numbers cannot contain one, so no shipped URL is affected.
 * `PercentEncoderJvmDifferentialTest` pins the equivalence over everything that can.
 */
object PercentEncoder {
    /**
     * `application/x-www-form-urlencoded` encoding of [value] over UTF-8, with **uppercase** hex
     * digits (as `java.net.URLEncoder` emits, and as the live-verified corpus contains: `%2C`,
     * `%3A`).
     */
    fun encode(value: String): String {
        val out = StringBuilder(value.length)
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            when {
                // Only single-byte (ASCII) values can be unreserved; a UTF-8 continuation byte is
                // always >= 0x80, so this can never pass a fragment of a multi-byte character.
                code < 0x80 && char in UNRESERVED -> out.append(char)
                char == ' ' -> out.append('+')
                else -> {
                    out.append('%')
                    out.append(HEX[code shr 4])
                    out.append(HEX[code and 0x0F])
                }
            }
        }
        return out.toString()
    }

    /**
     * `java.net.URLEncoder`'s `dontNeedEncoding` set, minus the space (handled above as `+`).
     * Note `*` is present and `~` is absent — that is URLEncoder's set, not RFC 3986's.
     */
    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.*"

    private const val HEX = "0123456789ABCDEF"
}

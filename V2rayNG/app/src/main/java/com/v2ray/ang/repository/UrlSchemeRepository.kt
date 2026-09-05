package com.v2ray.ang.repository

import android.net.Uri
import com.v2ray.ang.handler.AngConfigManager

/** Matches a surviving percent-escape; compiled once instead of per import. */
private val PERCENT_ESCAPE = Regex("%[0-9A-Fa-f]{2}")
private const val SCHEME_SEPARATOR = "://"
private const val FRAGMENT_MARKER = '#'
private const val LINE_MARKER = '\n'

/**
 * Outcome of an external import.
 *
 * A sealed type rather than the previous `Pair<Int, Int>`: the caller has to distinguish "the
 * payload was never usable" from "it was usable but matched nothing", because those two deserve
 * different messages, and a pair of anonymous Ints cannot express the first case at all.
 */
sealed interface UrlSchemeImport {

    /** Nothing decodable was supplied. */
    data object Unusable : UrlSchemeImport

    /** The import ran; counts may still be zero when the payload held no valid entry. */
    data class Done(
        val configCount: Int,
        val subscriptionCount: Int
    ) : UrlSchemeImport {
        val isEmpty: Boolean get() = configCount + subscriptionCount <= 0
    }
}

/**
 * Builds the canonical share URL out of an already-decoded payload.
 *
 * Merging the scheme-level [fragment] is what lets `v2rayng://install-sub?url=...#MyName` name the
 * new group. Top-level and free of [Uri] on purpose: this is the whole of the naming policy, and
 * keeping it pure is what makes it assertable in a JVM test.
 */
internal fun mergeFragment(decoded: String, fragment: String): String {
    if (decoded.isEmpty()) return ""

    val hint = fragment.trim()
    if (hint.isEmpty()) return decoded

    // Only a single-line payload can carry a meaningful fragment; a batch names its own entries.
    // `contains` short-circuits, unlike the previous lineSequence().count(), which walked a
    // possibly multi-megabyte payload to the end just to learn it had two lines.
    if (decoded.contains(LINE_MARKER)) return decoded

    // Cheaper and safer than Uri.parse on an arbitrary blob: a raw base64 subscription body is not
    // a URI, and parsing one only to read `.fragment` is wasted work.
    return if (decoded.contains(FRAGMENT_MARKER)) decoded else "$decoded$FRAGMENT_MARKER$hint"
}

/**
 * Data layer of the external URL-scheme entry point.
 *
 * Owns two concerns the ViewModel must not know about: turning whatever an external app handed us
 * into one canonical share URL, and running the import. Both happen inside [withIO], so the
 * ViewModel never picks a dispatcher and never touches [AngConfigManager] directly.
 *
 * Holds no Application: nothing here needs a Context, and an unused one only invites the data
 * layer to start reaching for Android services.
 */
open class UrlSchemeRepository : BaseRepository() {

    /**
     * Normalises [payload] and imports it into the ungrouped ("Default") group.
     *
     * `append = true` is deliberate and is the fix for the wiped-group bug: the previous
     * `append = false` combined with an empty subscription id made `parseBatchConfig` call
     * `removeServerViaSubid("")`, which deleted every ungrouped profile the user owned before
     * inserting the incoming one.
     *
     * @param allowDoubleDecode set only for `ACTION_VIEW`, whose query parameter has already been
     *        decoded once by [Uri]; share-sheet text is literal and must never be decoded
     */
    open suspend fun import(
        payload: String,
        fragment: String,
        allowDoubleDecode: Boolean
    ): UrlSchemeImport = withIO {
        val url = normalize(payload, fragment, allowDoubleDecode)
        if (url.isEmpty()) return@withIO UrlSchemeImport.Unusable

        val (configCount, subCount) = AngConfigManager.importBatchConfig(
            server = url,
            subid = "",
            append = true
        )
        UrlSchemeImport.Done(configCount, subCount)
    }

    private fun normalize(payload: String, fragment: String, allowDoubleDecode: Boolean): String {
        val raw = payload.trim()
        if (raw.isEmpty()) return ""

        val decoded = (if (allowDoubleDecode) decodeIfNeeded(raw) else raw).trim()
        return mergeFragment(decoded, fragment)
    }

    /**
     * Undoes one extra layer of percent-encoding, and only when there is evidence of one.
     *
     * Two rules, both learned the hard way. `Uri.decode` instead of `URLDecoder.decode`, because
     * the latter follows form-encoding and turns `+` into a space — `+` is a base64 character and
     * a legal password character, so that silently corrupted valid VMess and Shadowsocks links.
     * And the guard itself, because `Uri.getQueryParameter` has already decoded the value once:
     * decoding unconditionally would eat any literal `%` a sender embedded on purpose.
     */
    private fun decodeIfNeeded(raw: String): String = when {
        raw.contains(SCHEME_SEPARATOR) -> raw
        !PERCENT_ESCAPE.containsMatchIn(raw) -> raw
        else -> Uri.decode(raw)
    }
}

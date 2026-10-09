package com.v2ray.ang.core

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.zip.InflaterInputStream
import kotlin.io.encoding.Base64

/**
 * The server list Psiphon's client starts with. It is the signed, compressed list the client
 * downloads for itself, kept in the app's asset folder as psiphon_servers.dat: every build ships
 * the newest one, and the Asset files screen updates or replaces it like the geo files. The client
 * takes it only as a plain file of server entries, so the list is checked and unpacked beside the
 * identity files once per version of the file. A list without Psiphon's signature is not handed
 * on: the client trusts what it is given, and the file can be replaced by hand.
 */
object PsiphonServerList {

    /** Psiphon's public key for its server list, the one its client checks downloads with; the core carries the same key. */
    internal const val SIGNING_KEY =
        "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH" +
            "5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5" +
            "OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42Kcot" +
            "LFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6" +
            "/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7G" +
            "stZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1O" +
            "geF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8" +
            "u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz" +
            "31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xal" +
            "KxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="

    private const val ENTRIES_FILE = "psiphon-servers.txt"

    /** The core's line with what Psiphon reported as AvailableEgressRegions: the countries of every server it knows. */
    private val leavesFrom = Regex("""psiphon can leave from: ([A-Z]{2}(?: [A-Z]{2})*)\s*$""")
    private const val SOURCE_MARK = "psiphon-servers.source"

    /**
     * The entries file for the list in [assetDir], unpacked into [workDir] when the list is new
     * or has changed since; the entries kept from before when the list there cannot be used, with
     * the reason given to [onProblem], and null when there is no list at all.
     */
    fun entriesFile(assetDir: File, workDir: File, signingKey: String = SIGNING_KEY, onProblem: (IOException) -> Unit = {}): File? {
        val source = File(assetDir, AppConfig.PSIPHON_SERVERS_DAT)
        if (!source.isFile) return null
        val entries = File(workDir, ENTRIES_FILE)
        val mark = File(workDir, SOURCE_MARK)
        val stamp = "${source.length()}:${source.lastModified()}"
        if (entries.isFile && mark.isFile && runCatching { mark.readText() }.getOrNull() == stamp) return entries
        return try {
            val text = unpack(source.readBytes(), signingKey)
            // The daemon and the editor may both unpack a new list; each writes under a name of its own.
            val fresh = File(workDir, "$ENTRIES_FILE.${System.nanoTime()}.new")
            fresh.writeText(text)
            if (!fresh.renameTo(entries)) {
                entries.delete()
                if (!fresh.renameTo(entries)) throw IOException("could not put ${entries.name} in place")
            }
            mark.writeText(stamp)
            entries
        } catch (e: IOException) {
            onProblem(e)
            entries.takeIf { it.isFile }
        }
    }

    /**
     * When the bundled list was published, from the stamp the build writes beside it: the seconds
     * since the epoch of the download's Last-Modified header, as millis; 0 without a usable stamp.
     */
    fun publishedAt(stamp: String?): Long = stamp?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.times(1000) ?: 0L

    /**
     * Whether the bundled list, published at [publishedAt], goes over [copy] in the asset folder:
     * when there is no copy, or when the list was published after the copy was made and the copy
     * is not a file the user picked. A copy's time is when it was downloaded or, for one the app
     * copied out, when its list was published; a download holds the list as published up to that
     * moment, so a later publication is a newer list and an earlier one is not.
     */
    fun bundledListGoesOver(copy: File, publishedAt: Long, keptByUser: Boolean): Boolean =
        !copy.exists() || (!keptByUser && publishedAt > copy.lastModified())

    /**
     * The exit countries the servers in [entries] offer, as ISO 3166-1 alpha-2 codes, sorted. An
     * entry is one hex-encoded line, "address port secret certificate {json}", with the region in
     * the JSON; a line that does not read is skipped. Psiphon reports the same set, from the
     * servers it knows, as AvailableEgressRegions, and takes one of them as EgressRegion.
     */
    fun regions(entries: String): Set<String> = entries.lineSequence().mapNotNullTo(sortedSetOf()) { regionOf(it.trim()) }

    private fun regionOf(line: String): String? {
        if (line.isEmpty() || line.length % 2 != 0) return null
        val bytes = ByteArray(line.length / 2)
        for (index in bytes.indices) {
            val high = Character.digit(line[2 * index], 16)
            val low = Character.digit(line[2 * index + 1], 16)
            if (high < 0 || low < 0) return null
            bytes[index] = ((high shl 4) or low).toByte()
        }
        val json = bytes.toString(Charsets.UTF_8).split(' ', limit = 5).getOrNull(4) ?: return null
        val region = try {
            JsonParser.parseString(json).asJsonObject.get("region")?.takeIf { it.isJsonPrimitive }?.asString
        } catch (_: RuntimeException) {
            null
        }
        return region?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all(Char::isLetter) }
    }

    /**
     * The countries in [line] when it is the core's report of what Psiphon can leave from, else
     * null. Psiphon reports them at every start, from every server it knows, discovered ones
     * included, so they can name countries the app's own list does not.
     */
    fun regionsOf(line: String): List<String>? = leavesFrom.find(line)?.groupValues?.get(1)?.split(' ')

    /** Keeps [regions] as Psiphon's last report, in place of the one before: the report is the whole set, so the last one is the truth. */
    fun remember(regions: Collection<String>) {
        MmkvManager.encodeSettings(AppConfig.PREF_PSIPHON_REGIONS, regions.toSortedSet().joinToString(","))
    }

    /** Psiphon's last report of the countries it can leave from; empty before the first. */
    fun remembered(): Set<String> =
        MmkvManager.decodeSettingsString(AppConfig.PREF_PSIPHON_REGIONS).orEmpty().split(',').filterTo(sortedSetOf()) { it.length == 2 }

    /** Forgets the last report, for when the datastore it described is cleared. */
    fun forgetRemembered() {
        MmkvManager.encodeSettings(AppConfig.PREF_PSIPHON_REGIONS, "")
    }

    /**
     * The server entries inside [packed], a signed, compressed list as Psiphon writes it; throws
     * when it is not one, or is not signed with [signingKey]. Psiphon names the key by the digest
     * of its text as written, not of the key's bytes, and signs the entry text with it.
     */
    @Throws(IOException::class)
    internal fun unpack(packed: ByteArray, signingKey: String): String {
        val text = try {
            InflaterInputStream(ByteArrayInputStream(packed)).use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (e: IOException) {
            throw IOException("the list is not compressed the way Psiphon writes it", e)
        }
        val json = try {
            JsonParser.parseString(text).asJsonObject
        } catch (e: RuntimeException) {
            throw IOException("the list is not the package Psiphon writes", e)
        }
        val data = json.get("data")?.takeIf { it.isJsonPrimitive }?.asString ?: throw IOException("the list carries no entries")
        val keyDigest = json.get("signingPublicKeyDigest")?.takeIf { it.isJsonPrimitive }?.asString ?: throw IOException("the list names no key")
        val signature = json.get("signature")?.takeIf { it.isJsonPrimitive }?.asString ?: throw IOException("the list is not signed")
        try {
            val expected = MessageDigest.getInstance("SHA-256").digest(signingKey.toByteArray(Charsets.US_ASCII))
            if (!Base64.decode(keyDigest).contentEquals(expected)) throw IOException("the list is signed with another key")
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(signingKey)))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(key)
            verifier.update(data.toByteArray(Charsets.UTF_8))
            if (!verifier.verify(Base64.decode(signature))) throw IOException("the signature of the list does not check out")
        } catch (e: GeneralSecurityException) {
            throw IOException("the signature of the list cannot be checked", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("the list is not encoded the way Psiphon writes it", e)
        }
        if (data.lineSequence().none { it.isNotBlank() }) throw IOException("the list is empty")
        return data
    }
}

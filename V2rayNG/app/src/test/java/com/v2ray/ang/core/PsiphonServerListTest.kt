package com.v2ray.ang.core

import com.google.gson.Gson
import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.zip.DeflaterOutputStream
import kotlin.io.encoding.Base64

class PsiphonServerListTest {

    @TempDir
    lateinit var folder: File

    private fun newFolder(name: String): File = File(folder, name).apply { check(mkdir()) { "could not create $this" } }

    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val keyText = Base64.encode(pair.public.encoded)
    private val entries = "0 0 0 0 {\"ipAddress\":\"203.0.113.9\"}\n0 0 0 0 {\"ipAddress\":\"203.0.113.10\"}\n"

    private fun sign(data: String, key: PrivateKey = pair.private): String =
        Base64.encode(Signature.getInstance("SHA256withRSA").run { initSign(key); update(data.toByteArray()); sign() })

    private fun digestOf(text: String): String = Base64.encode(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    /** A list the way Psiphon writes one: the package as JSON, compressed. */
    private fun pack(data: String = entries, signature: String = sign(data), keyDigest: String = digestOf(keyText)): ByteArray {
        val json = """{"data":${Gson().toJson(data)},"signingPublicKeyDigest":"$keyDigest","signature":"$signature"}"""
        return ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(json.toByteArray()) } }.toByteArray()
    }

    @Test
    fun aListSignedWithTheKeyUnpacksToItsEntries() {
        assertEquals(entries, PsiphonServerList.unpack(pack(), keyText))
    }

    @Test
    fun aListThatIsNotPsiphonsIsRefused() {
        // Entries added after signing.
        assertThrows(IOException::class.java) { PsiphonServerList.unpack(pack(data = entries + "0 0 0 0 {}\n", signature = sign(entries)), keyText) }
        // Signed with another key that names ours.
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertThrows(IOException::class.java) { PsiphonServerList.unpack(pack(signature = sign(entries, other.private)), keyText) }
        // Naming another key.
        assertThrows(IOException::class.java) { PsiphonServerList.unpack(pack(keyDigest = digestOf("someone else")), keyText) }
        // Not a list at all, and a list of nothing.
        assertThrows(IOException::class.java) { PsiphonServerList.unpack("not a list".toByteArray(), keyText) }
        assertThrows(IOException::class.java) { PsiphonServerList.unpack(pack(data = "\n"), keyText) }
    }

    @Test
    fun theCountriesOnOfferAreReadFromTheEntries() {
        fun entry(region: String?) =
            ("203.0.113.9 22 secret certificate " + (if (region == null) "{}" else "{\"ipAddress\":\"203.0.113.9\",\"region\":\"$region\"}"))
                .toByteArray().joinToString("") { "%02x".format(it) }
        val text = listOf(entry("de"), entry("US"), entry("DE"), entry(null), "not hex at all", "", "abc").joinToString("\n")

        assertEquals(listOf("DE", "US"), PsiphonServerList.regions(text).toList())
        assertTrue(PsiphonServerList.regions("").isEmpty())

        // The core's report of what Psiphon can leave from, as it comes through the log.
        assertEquals(
            listOf("US", "CA", "DE"),
            PsiphonServerList.regionsOf("[2026-09-24T20:43:04.197Z INFO  aether::psiphon] [*] psiphon can leave from: US CA DE")
        )
        assertNull(PsiphonServerList.regionsOf("[*] psiphon reached a server at 203.0.113.9"))
        assertNull(PsiphonServerList.regionsOf("psiphon can leave from: usa"))
    }

    @Test
    fun theBundledListGoesOverACopyOnlyWhenItWasPublishedLater() {
        val copy = File(newFolder("assets"), AppConfig.PSIPHON_SERVERS_DAT)
        assertTrue(PsiphonServerList.bundledListGoesOver(copy, 0L, keptByUser = false))
        assertTrue(PsiphonServerList.bundledListGoesOver(copy, 0L, keptByUser = true))

        copy.writeBytes(pack())
        copy.setLastModified(1_700_000_000_000L)
        // Published after the copy was made: a newer list.
        assertTrue(PsiphonServerList.bundledListGoesOver(copy, 1_700_000_100_000L, keptByUser = false))
        // Published at or before the copy's time: the copy holds that list already.
        assertFalse(PsiphonServerList.bundledListGoesOver(copy, 1_700_000_000_000L, keptByUser = false))
        assertFalse(PsiphonServerList.bundledListGoesOver(copy, 1_600_000_000_000L, keptByUser = false))
        // Without a stamp there is no telling, and the copy stays.
        assertFalse(PsiphonServerList.bundledListGoesOver(copy, 0L, keptByUser = false))
        // A file the user picked stays whatever the build brings.
        assertFalse(PsiphonServerList.bundledListGoesOver(copy, 1_700_000_100_000L, keptByUser = true))
    }

    @Test
    fun thePublicationStampIsSecondsSinceTheEpochOrNothing() {
        assertEquals(1_790_000_000_000L, PsiphonServerList.publishedAt("1790000000\n"))
        assertEquals(1_790_000_000_000L, PsiphonServerList.publishedAt(" 1790000000 "))
        assertEquals(0L, PsiphonServerList.publishedAt(null))
        assertEquals(0L, PsiphonServerList.publishedAt(""))
        assertEquals(0L, PsiphonServerList.publishedAt("yesterday"))
        assertEquals(0L, PsiphonServerList.publishedAt("-5"))
    }

    @Test
    fun theEntriesAreWrittenOncePerVersionOfTheListAndKeptWhenTheNextOneIsBroken() {
        val assets = newFolder("assets")
        val work = newFolder("work")
        assertNull(PsiphonServerList.entriesFile(assets, work, keyText))

        val source = File(assets, AppConfig.PSIPHON_SERVERS_DAT).apply { writeBytes(pack()) }
        val first = PsiphonServerList.entriesFile(assets, work, keyText)!!
        assertEquals(entries, first.readText())
        // The same version of the list is not unpacked again.
        first.writeText("left alone")
        assertEquals("left alone", PsiphonServerList.entriesFile(assets, work, keyText)!!.readText())

        // A new version is.
        val more = entries + "0 0 0 0 {\"ipAddress\":\"203.0.113.11\"}\n"
        source.writeBytes(pack(data = more))
        source.setLastModified(source.lastModified() + 5_000)
        assertEquals(more, PsiphonServerList.entriesFile(assets, work, keyText)!!.readText())

        // A broken replacement leaves the last good entries in place, and says why.
        source.writeBytes("garbage".toByteArray())
        source.setLastModified(source.lastModified() + 5_000)
        val problems = mutableListOf<IOException>()
        assertEquals(more, PsiphonServerList.entriesFile(assets, work, keyText, problems::add)!!.readText())
        assertEquals(1, problems.size)

        // Without the list there is nothing to hand on.
        source.delete()
        assertNull(PsiphonServerList.entriesFile(assets, work, keyText))
    }
}

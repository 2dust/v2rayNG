package com.v2ray.ang.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class ZipUtilTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun unzipToFolderExtractsNestedFile() {
        val archive = createArchive("nested/config" to "value".toByteArray())
        val destination = File(temporaryFolder.root, "destination")

        assertTrue(ZipUtil.unzipToFolder(archive, destination.absolutePath))
        assertEquals("value", File(destination, "nested/config").readText())
    }

    @Test
    fun extractArchiveRejectsPathTraversal() {
        val archive = createArchive("../outside" to "overwrite".toByteArray())
        val destination = File(temporaryFolder.root, "destination")
        val outside = File(temporaryFolder.root, "outside")

        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(archive, destination, extractionLimits())
        }

        assertFalse(outside.exists())
        assertFalse(destination.exists())
    }

    @Test
    fun extractArchiveRejectsOversizedArchive() {
        val archive = createArchive("config" to byteArrayOf(1))
        val destination = File(temporaryFolder.root, "destination")

        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(
                archive,
                destination,
                extractionLimits(maxArchiveBytes = archive.length() - 1),
            )
        }

        assertFalse(destination.exists())
    }

    @Test
    fun extractArchiveRejectsTooManyEntries() {
        val archive = createArchive(
            "first" to byteArrayOf(1),
            "second" to byteArrayOf(2),
        )
        val destination = File(temporaryFolder.root, "destination")

        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(
                archive,
                destination,
                extractionLimits(maxEntries = 1),
            )
        }

        assertFalse(destination.exists())
    }

    @Test
    fun extractArchiveRejectsOversizedEntry() {
        val archive = createArchive("config" to ByteArray(5))
        val destination = File(temporaryFolder.root, "destination")

        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(
                archive,
                destination,
                extractionLimits(maxEntryBytes = 4),
            )
        }

        assertFalse(destination.exists())
    }

    @Test
    fun extractArchiveRejectsExcessiveTotalSize() {
        val archive = createArchive(
            "first" to ByteArray(3),
            "second" to ByteArray(3),
        )
        val destination = File(temporaryFolder.root, "destination")

        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(
                archive,
                destination,
                extractionLimits(maxTotalBytes = 5),
            )
        }

        assertFalse(destination.exists())
    }

    @Test
    fun extractArchiveRejectsExcessiveCompressionRatio() {
        val archive = createArchive("config" to ByteArray(4096))
        val destination = File(temporaryFolder.root, "destination")

        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(
                archive,
                destination,
                extractionLimits(maxCompressionRatio = 2.0),
            )
        }

        assertFalse(destination.exists())
    }

    @Test
    fun extractArchiveRejectsDuplicateCanonicalDestination() {
        val archive = createArchive(
            "config" to byteArrayOf(1),
            "nested/../config" to byteArrayOf(2),
        )
        val destination = File(temporaryFolder.root, "destination")

        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(archive, destination, extractionLimits())
        }

        assertFalse(destination.exists())
    }

    @Test
    fun extractArchiveRejectsEntryBelowFile() {
        val archive = createArchive(
            "parent" to byteArrayOf(1),
            "parent/child" to byteArrayOf(2),
        )
        val destination = File(temporaryFolder.root, "destination")

        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(archive, destination, extractionLimits())
        }

        assertFalse(destination.exists())
    }

    private fun createArchive(vararg entries: Pair<String, ByteArray>): File {
        val archive = temporaryFolder.newFile()
        ZipOutputStream(archive.outputStream()).use { output ->
            entries.forEach { (name, contents) ->
                output.putNextEntry(ZipEntry(name))
                output.write(contents)
                output.closeEntry()
            }
        }
        return archive
    }

    private fun extractionLimits(
        maxArchiveBytes: Long = 1024L * 1024L,
        maxEntries: Int = 10,
        maxEntryBytes: Long = 16L * 1024L,
        maxTotalBytes: Long = 32L * 1024L,
        maxCompressionRatio: Double = 1000.0,
    ) = ZipUtil.ExtractionLimits(
        maxArchiveBytes = maxArchiveBytes,
        maxEntries = maxEntries,
        maxEntryBytes = maxEntryBytes,
        maxTotalBytes = maxTotalBytes,
        maxCompressionRatio = maxCompressionRatio,
    )
}

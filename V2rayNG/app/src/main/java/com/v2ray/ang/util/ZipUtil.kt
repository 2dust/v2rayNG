package com.v2ray.ang.util

import com.v2ray.ang.AppConfig
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipUtil {
    private const val BUFFER_SIZE = 4096
    private const val MEBIBYTE = 1024L * 1024L

    private val DEFAULT_EXTRACTION_LIMITS = ExtractionLimits(
        maxArchiveBytes = 128L * MEBIBYTE,
        maxEntries = 32,
        maxEntryBytes = 128L * MEBIBYTE,
        maxTotalBytes = 256L * MEBIBYTE,
        maxCompressionRatio = 1000.0,
    )

    internal class ExtractionLimits(
        val maxArchiveBytes: Long,
        val maxEntries: Int,
        val maxEntryBytes: Long,
        val maxTotalBytes: Long,
        val maxCompressionRatio: Double,
    ) {
        init {
            require(maxArchiveBytes > 0)
            require(maxEntries > 0)
            require(maxEntryBytes > 0)
            require(maxTotalBytes > 0)
            require(maxCompressionRatio >= 1.0)
        }
    }

    /**
     * Zip the contents of a folder.
     *
     * @param folderPath The path to the folder to zip.
     * @param outputZipFilePath The path to the output zip file.
     * @return True if the operation is successful, false otherwise.
     * @throws IOException If an I/O error occurs.
     */
    @Throws(IOException::class)
    fun zipFromFolder(folderPath: String, outputZipFilePath: String): Boolean {
        val buffer = ByteArray(BUFFER_SIZE)

        try {
            if (folderPath.isEmpty() || outputZipFilePath.isEmpty()) {
                return false
            }

            val filesToCompress = ArrayList<String>()
            val directory = File(folderPath)
            if (directory.isDirectory) {
                directory.listFiles()?.forEach {
                    if (it.isFile) {
                        filesToCompress.add(it.absolutePath)
                    }
                }
            }
            if (filesToCompress.isEmpty()) {
                return false
            }

            val zos = ZipOutputStream(FileOutputStream(outputZipFilePath))

            filesToCompress.forEach { file ->
                val ze = ZipEntry(File(file).name)
                zos.putNextEntry(ze)
                val inputStream = FileInputStream(file)
                while (true) {
                    val len = inputStream.read(buffer)
                    if (len <= 0) break
                    zos.write(buffer, 0, len)
                }

                inputStream.close()
            }

            zos.closeEntry()
            zos.close()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to zip folder", e)
            return false
        }
        return true
    }

    /**
     * Unzip the contents of a zip file to a folder.
     *
     * @param zipFile The zip file to unzip.
     * @param destDirectory The destination directory.
     * @return True if the operation is successful, false otherwise.
     * @throws IOException If an I/O error occurs.
     */
    @Throws(IOException::class)
    fun unzipToFolder(zipFile: File, destDirectory: String): Boolean {
        return try {
            extractArchive(zipFile, File(destDirectory), DEFAULT_EXTRACTION_LIMITS)
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to unzip file", e)
            false
        }
    }

    internal fun extractArchive(
        zipFile: File,
        destination: File,
        limits: ExtractionLimits,
    ) {
        if (!zipFile.isFile || zipFile.length() > limits.maxArchiveBytes) {
            throw ZipException("ZIP archive is missing or exceeds the compressed-size limit")
        }

        val root = destination.canonicalFile
        ZipFile(zipFile).use { zip ->
            val entries = validateEntries(zip, root, limits)
            val rootCreated = prepareDestination(root)
            try {
                extractEntries(zip, entries, limits)
            } catch (error: Throwable) {
                root.listFiles()?.forEach { it.deleteRecursively() }
                if (rootCreated) root.delete()
                throw error
            }
        }
    }

    private fun validateEntries(
        zip: ZipFile,
        root: File,
        limits: ExtractionLimits,
    ): Map<File, ZipEntry> {
        val entriesByTarget = LinkedHashMap<File, ZipEntry>()
        var totalBytes = 0L
        val entries = zip.entries()

        while (entries.hasMoreElements()) {
            if (entriesByTarget.size >= limits.maxEntries) {
                throw ZipException("ZIP archive exceeds the entry-count limit")
            }

            val entry = entries.nextElement()
            if (entry.name.isEmpty()) {
                throw ZipException("ZIP archive contains an empty entry name")
            }
            if (entry.method != ZipEntry.STORED && entry.method != ZipEntry.DEFLATED) {
                throw ZipException("ZIP archive contains an unsupported compression method")
            }

            val target = resolveEntryTarget(root, entry)
            if (entriesByTarget.put(target, entry) != null) {
                throw ZipException("ZIP archive contains duplicate entry destinations")
            }

            val entryBytes = entry.size
            val compressedBytes = entry.compressedSize
            if (entryBytes < 0L || compressedBytes < 0L) {
                throw ZipException("ZIP archive contains an entry with an unknown size")
            }
            if (entry.isDirectory && entryBytes != 0L) {
                throw ZipException("ZIP archive contains a non-empty directory entry")
            }
            if (entryBytes > limits.maxEntryBytes) {
                throw ZipException("ZIP archive entry exceeds the expanded-size limit")
            }
            if (totalBytes > limits.maxTotalBytes - entryBytes) {
                throw ZipException("ZIP archive exceeds the total expanded-size limit")
            }
            if (
                entryBytes > 0L && (
                    compressedBytes == 0L ||
                        entryBytes.toDouble() / compressedBytes > limits.maxCompressionRatio
                )
            ) {
                throw ZipException("ZIP archive entry exceeds the compression-ratio limit")
            }

            totalBytes += entryBytes
        }

        entriesByTarget.forEach { (target, _) ->
            var parent = target.parentFile
            while (parent != null && parent != root) {
                if (entriesByTarget[parent]?.isDirectory == false) {
                    throw ZipException("ZIP archive places an entry below a file")
                }
                parent = parent.parentFile
            }
        }
        return entriesByTarget
    }

    private fun resolveEntryTarget(root: File, entry: ZipEntry): File {
        val target = File(root, entry.name).canonicalFile
        val rootPrefix = root.path + File.separator
        if (target == root || !target.path.startsWith(rootPrefix)) {
            throw ZipException("ZIP entry resolves outside the destination directory")
        }
        return target
    }

    private fun prepareDestination(root: File): Boolean {
        if (root.exists()) {
            if (!root.isDirectory || root.listFiles()?.isNotEmpty() != false) {
                throw IOException("ZIP destination must be an empty directory")
            }
            return false
        }
        if (!root.mkdirs()) {
            throw IOException("Unable to create ZIP destination directory")
        }
        return true
    }

    private fun extractEntries(
        zip: ZipFile,
        entries: Map<File, ZipEntry>,
        limits: ExtractionLimits,
    ) {
        var totalBytes = 0L
        entries.forEach { (target, entry) ->
            if (entry.isDirectory) {
                if (!target.isDirectory && !target.mkdirs()) {
                    throw IOException("Unable to create ZIP entry directory")
                }
                return@forEach
            }

            val parent = target.parentFile
                ?: throw ZipException("ZIP entry has no destination parent")
            if (!parent.isDirectory && !parent.mkdirs()) {
                throw IOException("Unable to create ZIP entry parent directory")
            }
            if (target.exists()) {
                throw ZipException("ZIP entry would overwrite an existing file")
            }

            zip.getInputStream(entry).use { input ->
                BufferedOutputStream(FileOutputStream(target)).use { output ->
                    totalBytes += copyEntry(input, output, entry.size, totalBytes, limits)
                }
            }
        }
    }

    private fun copyEntry(
        input: InputStream,
        output: BufferedOutputStream,
        expectedBytes: Long,
        currentTotalBytes: Long,
        limits: ExtractionLimits,
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var entryBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            val readBytes = read.toLong()
            val remainingEntryBytes = limits.maxEntryBytes - entryBytes
            val remainingTotalBytes = limits.maxTotalBytes - currentTotalBytes - entryBytes
            if (readBytes > remainingEntryBytes || readBytes > remainingTotalBytes
            ) {
                throw ZipException("ZIP entry exceeds the expanded-size limit while extracting")
            }
            output.write(buffer, 0, read)
            entryBytes += readBytes
        }
        if (entryBytes != expectedBytes) {
            throw ZipException("ZIP entry expanded size differs from its declared size")
        }
        return entryBytes
    }
}

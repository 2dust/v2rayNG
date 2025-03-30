package com.v2ray.ang.util

import android.util.Log
import com.v2ray.ang.AppConfig
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipUtil {
    private const val BUFFER_SIZE = 4096

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
            Log.e(AppConfig.TAG, "Failed to zip folder", e)
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
        File(destDirectory).run {
            if (!exists()) {
                mkdirs()
            }
        }
        try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    zip.getInputStream(entry).use { input ->
                        val filePath = destDirectory + File.separator + entry.name
                        if (!entry.isDirectory) {
                            extractFile(input, filePath)
                        } else {
                            val dir = File(filePath)
                            dir.mkdir()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to unzip file", e)
            return false
        }
        return true
    }

    /**
     * Extract a file from an input stream.
     *
     * @param inputStream The input stream to read from.
     * @param destFilePath The destination file path.
     * @throws IOException If an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun extractFile(inputStream: InputStream, destFilePath: String) {
        val bos = BufferedOutputStream(FileOutputStream(destFilePath))
        val bytesIn = ByteArray(BUFFER_SIZE)
        var read: Int
        while (inputStream.read(bytesIn).also { read = it } != -1) {
            bos.write(bytesIn, 0, read)
        }
        bos.close()
    }
}
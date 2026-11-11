package com.v2ray.ang.util

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BackupCipherTest {

    @TempDir
    lateinit var dir: File

    private fun sample(size: Int = 40_000): File {
        val file = File(dir, "backup.zip")
        val bytes = ByteArray(size) { (it * 31 % 251).toByte() }
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun plainArchiveIsNotEncrypted() {
        assertFalse(BackupCipher.isEncrypted(sample()))
    }

    @Test
    fun encryptedArchiveRoundTrips() {
        val source = sample()
        val encrypted = File(dir, "backup.rxbk")
        val restored = File(dir, "restored.zip")

        assertTrue(BackupCipher.encrypt(source, encrypted, "s3cret".toCharArray()))
        assertTrue(BackupCipher.isEncrypted(encrypted))
        assertTrue(BackupCipher.decrypt(encrypted, restored, "s3cret".toCharArray()))
        assertArrayEquals(source.readBytes(), restored.readBytes())
    }

    @Test
    fun wrongPasswordLeavesNothingBehind() {
        val source = sample()
        val encrypted = File(dir, "backup.rxbk")
        val restored = File(dir, "restored.zip")
        assertTrue(BackupCipher.encrypt(source, encrypted, "s3cret".toCharArray()))

        assertFalse(BackupCipher.decrypt(encrypted, restored, "wrong".toCharArray()))
        assertFalse(restored.exists())
        assertTrue(encrypted.exists())
    }

    @Test
    fun truncatedFileIsRejected() {
        val encrypted = File(dir, "backup.rxbk")
        assertTrue(BackupCipher.encrypt(sample(), encrypted, "s3cret".toCharArray()))
        val truncated = File(dir, "cut.rxbk")
        truncated.writeBytes(encrypted.readBytes().copyOf(20))

        assertFalse(BackupCipher.decrypt(truncated, File(dir, "out.zip"), "s3cret".toCharArray()))
    }
}

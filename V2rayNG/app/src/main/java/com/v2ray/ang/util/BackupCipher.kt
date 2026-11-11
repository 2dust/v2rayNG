package com.v2ray.ang.util

import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for configuration backups: AES-256-GCM with a key derived from the password.
 *
 * File layout (all big endian):
 * `magic "RXBACKUP1" | version 1 | iterations 4 | salt 16 | iv 12 | ciphertext + 16-byte GCM tag`
 *
 * A plain `.zip` stays readable, so backups made without a password keep working. The key
 * derivation is PBKDF2-HMAC-SHA1 because the SHA-256 variant only exists from API 26 on and the
 * app supports API 24; the iteration count carries the cost instead, and the header records it so
 * older backups can always be opened. The GCM tag makes a wrong password fail before anything is
 * written to the restored configuration.
 */
object BackupCipher {

    /** Extension used for encrypted backups; the plain archive keeps `.zip`. */
    const val EXTENSION = "rxbk"

    private const val MAGIC = "RXBACKUP1"
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val HEADER_BYTES = 9 + 1 + 4 + SALT_BYTES + IV_BYTES
    private const val BUFFER = 8 * 1024

    /** True when [file] starts with our header, i.e. it needs a password. */
    fun isEncrypted(file: File): Boolean = try {
        val prefix = ByteArray(MAGIC.length)
        val read = file.inputStream().use { it.readFully(prefix) }
        read == prefix.size && String(prefix, Charsets.US_ASCII) == MAGIC
    } catch (e: Exception) {
        false
    }

    /** Writes [source] to [target] encrypted with [password]. */
    fun encrypt(source: File, target: File, password: CharArray): Boolean = try {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt, ITERATIONS),
            GCMParameterSpec(TAG_BITS, iv),
        )

        target.outputStream().use { output ->
            output.write(MAGIC.toByteArray(Charsets.US_ASCII))
            output.write(VERSION.toInt())
            output.write(ByteArray(4).apply { putInt(0, ITERATIONS) })
            output.write(salt)
            output.write(iv)

            source.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    val chunk = cipher.update(buffer, 0, read)
                    if (chunk != null && chunk.isNotEmpty()) output.write(chunk)
                }
            }
            output.write(cipher.doFinal())
        }
        true
    } catch (e: Exception) {
        target.delete()
        false
    }

    /**
     * Decrypts [source] into [target].
     *
     * @return false for a wrong password or a damaged file; [target] is removed in that case.
     */
    fun decrypt(source: File, target: File, password: CharArray): Boolean = try {
        val header = ByteArray(HEADER_BYTES)
        source.inputStream().use { input ->
            if (input.readFully(header) != HEADER_BYTES) return false
            if (String(header, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) return false
            if (header[MAGIC.length] != VERSION) return false

            val iterations = header.getInt(MAGIC.length + 1)
            if (iterations <= 0) return false
            val salt = header.copyOfRange(MAGIC.length + 5, MAGIC.length + 5 + SALT_BYTES)
            val iv = header.copyOfRange(
                MAGIC.length + 5 + SALT_BYTES,
                HEADER_BYTES,
            )

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, salt, iterations),
                GCMParameterSpec(TAG_BITS, iv),
            )

            target.outputStream().use { output ->
                // GCM decryption is all-or-nothing in the JDK provider: update() buffers the
                // ciphertext and doFinal() produces the plaintext, so the allocating overloads are
                // used here. The in-place ones would need a buffer as large as the whole backup.
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    val chunk = cipher.update(buffer, 0, read)
                    if (chunk != null && chunk.isNotEmpty()) output.write(chunk)
                }
                // Throws when the GCM tag does not match, i.e. for a wrong password.
                val tail = cipher.doFinal()
                if (tail.isNotEmpty()) output.write(tail)
            }
        }
        true
    } catch (e: Exception) {
        target.delete()
        false
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec)
            return SecretKeySpec(key.encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private fun ByteArray.getInt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}

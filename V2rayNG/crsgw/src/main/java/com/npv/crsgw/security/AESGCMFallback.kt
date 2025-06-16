package com.npv.crsgw.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AESGCMFallback(private val context: Context) : AESGCMCipher {
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_PREF = "fallback_aes_key"
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 128
    }

    private fun getOrCreateKey(): SecretKey {
        val prefs = context.getSharedPreferences(KEY_PREF, Context.MODE_PRIVATE)
        val existing = prefs.getString(local_aes_key_alias, null)
        return if (existing != null) {
            val decoded = Base64.decode(existing, Base64.NO_WRAP)
            SecretKeySpec(decoded, "AES")
        } else {
            val key = ByteArray(16)
            SecureRandom().nextBytes(key)
            prefs.edit().putString(local_aes_key_alias, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
            SecretKeySpec(key, "AES")
        }
    }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), spec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(encryptedBase64: String): String {
        val allBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = allBytes.copyOfRange(0, IV_SIZE)
        val cipherText = allBytes.copyOfRange(IV_SIZE, allBytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }
}
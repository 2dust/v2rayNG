package com.npv.crsgw.security

interface AESGCMCipher {
    val local_aes_key_alias: String
        get() = "local_aes_key_alias"

    fun encrypt(plainText: String): String
    fun decrypt(encryptedBase64: String): String
}
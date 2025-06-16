package com.npv.crsgw.security

import android.content.Context
import android.os.Build

object AESGCMCompat {
    private lateinit var delegate: AESGCMCipher

    fun init(context: Context) {
        delegate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AESGCMKeystore()
        } else {
            AESGCMFallback(context)
        }
    }

    fun encrypt(plainText: String): String = delegate.encrypt(plainText)
    fun decrypt(encryptedBase64: String): String = delegate.decrypt(encryptedBase64)
}

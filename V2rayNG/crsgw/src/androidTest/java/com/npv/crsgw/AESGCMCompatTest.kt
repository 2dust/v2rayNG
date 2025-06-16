package com.npv.crsgw

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.npv.crsgw.security.AESGCMCompat
import com.npv.crsgw.security.AESGCMKeystore

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AESGCMCompatTest {

    @Before
    fun setup() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.npv.crsgw.test", appContext.packageName)
        AESGCMCompat.init(appContext)
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.npv.crsgw.test", appContext.packageName)
        AESGCMCompat.init(appContext)
    }

    @Test
    fun encrypt_and_decrypt_should_return_original_text() {
        val original = "Hello AES-GCM Keystore"
        // AESGCMCompat.encrypt(original)
        // val aes = AESGCMKeystore()
        val encrypted = AESGCMCompat.encrypt(original)
        val decrypted = AESGCMCompat.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun encrypt_same_input_should_produce_different_ciphertext() {
        val input = "same input"
        val aes = AESGCMKeystore()
        val encrypted1 = aes.encrypt(input)
        val encrypted2 = aes.encrypt(input)
        assertNotEquals(encrypted1, encrypted2)
    }

    @Test
    fun decrypt_invalid_base64_should_throw_exception() {
        val aes = AESGCMKeystore()
        try {
            aes.decrypt("not_base64_data")
            fail("Expected exception not thrown")
        } catch (_: Exception) {
            // Success
        }
    }
}
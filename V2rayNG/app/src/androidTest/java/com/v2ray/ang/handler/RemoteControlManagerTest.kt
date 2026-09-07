package com.v2ray.ang.handler

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.tencent.mmkv.MMKV
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Exercise real package signatures and native MMKV locking, which cannot be mocked inline on the JVM. */
class RemoteControlManagerTest {
    @Test fun grantsPersistAndRevocationInvalidatesSavedCapabilities() {
        val context = isolatedContext()
        assertTrue(RemoteControlManager.selectedPackages(context).isEmpty())
        assertNull(RemoteControlManager.configurationToken(context, "android"))
        RemoteControlManager.setSelectedPackages(context, listOf("android"))
        val token = RemoteControlManager.configurationToken(context, "android")!!
        assertEquals(64, token.length)
        assertEquals(setOf("android"), RemoteControlManager.selectedPackages(context))
        assertTrue(RemoteControlManager.isAuthorized(context, -1, "android", token))
        assertFalse(RemoteControlManager.isAuthorized(context, -1, "android", null))
        assertNull(RemoteControlManager.configurationToken(context, null))
        RemoteControlManager.setSelectedPackages(context, listOf("android"))
        assertEquals(token, RemoteControlManager.configurationToken(context, "android"))
        RemoteControlManager.setSelectedPackages(context, emptyList())
        assertFalse(RemoteControlManager.isAuthorized(context, -1, "android", token))
        RemoteControlManager.setSelectedPackages(context, listOf("android"))
        assertNotEquals(token, RemoteControlManager.configurationToken(context, "android"))
        assertFalse(RemoteControlManager.isAuthorized(context, -1, "android", token))
        RemoteControlManager.setSelectedPackages(context, emptyList())
    }

    @Test fun failedSelectionDoesNotPartiallyAuthorizeApps() {
        val context = isolatedContext()
        RemoteControlManager.setSelectedPackages(context, listOf("android"))
        val token = RemoteControlManager.configurationToken(context, "android")
        assertThrows(IllegalStateException::class.java) {
            RemoteControlManager.setSelectedPackages(context, listOf("android", "com.v2ray.nonexistent.qa"))
        }
        assertEquals(token, RemoteControlManager.configurationToken(context, "android"))
        RemoteControlManager.setSelectedPackages(context, emptyList())
    }

    @Test fun malformedStorageFailsClosed() {
        val context = isolatedContext()
        for (json in listOf("{", "null", "[null]", "[{\"packageName\":\"android\"}]")) {
            MmkvManager.withRemoteControlStorage(context) { it.encode("grants", json) }
            assertTrue(RemoteControlManager.selectedPackages(context).isEmpty())
            assertNull(RemoteControlManager.configurationToken(context, "android"))
            assertFalse(RemoteControlManager.isAuthorized(context, -1, "android", "forged"))
        }
    }

    @Test fun configurationBackupExcludesRemoteGrants() {
        val context = isolatedContext()
        RemoteControlManager.setSelectedPackages(context, listOf("android"))
        val backup = File(context.cacheDir, "remote-control-backup-test-${UUID.randomUUID()}")
        try {
            assertTrue(MMKV.backupAllToDirectory(backup.absolutePath) > 0)
            assertFalse(backup.resolve("REMOTE_CONTROL").exists())
            assertFalse(backup.resolve("REMOTE_CONTROL.crc").exists())
        } finally {
            backup.deleteRecursively()
            RemoteControlManager.setSelectedPackages(context, emptyList())
        }
    }

    private fun isolatedContext(): ContextWrapper {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.noBackupFilesDir, "remote-control-test-${UUID.randomUUID()}")
        return object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
    }
}

package com.v2ray.ang.ui.main

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerDeleteTargetTest {
    @Test
    fun pendingDeletionRestoresItsGuidAndDisplayName() {
        val scope = SaverScope { it is String }
        for (guid in listOf("first-guid", "second-guid")) {
            for (name in listOf("Same name", "", "Профиль\n服务器 100%")) {
                val target = ServerDeleteTarget(guid, name)
                val saved = with(ServerDeleteTarget.Saver) { scope.save(target) }

                assertEquals(listOf(guid, name), saved)
                assertEquals(target, ServerDeleteTarget.Saver.restore(requireNotNull(saved)))
            }
        }
    }

    @Test
    fun noPendingDeletionHasNoSavedTarget() {
        val saved = with(ServerDeleteTarget.Saver) { SaverScope { it is String }.save(null) }

        assertNull(saved)
    }
}

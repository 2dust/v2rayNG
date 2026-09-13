package com.v2ray.ang.ui.main

import androidx.compose.runtime.saveable.SaverScope
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerMenuDispatchTest {
    @Test
    fun pendingDeletionRestoresOnlyItsGuidAndDisplayName() {
        val scope = SaverScope { it is String }
        for (guid in listOf("first-guid", "second-guid")) {
            for (name in listOf("Same name", "", "Профиль\n服务器")) {
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

    @Test
    fun menuDispatchPreservesActionsAndRequestsDeletionSeparately() {
        val profile = ProfileItem(configType = EConfigType.VMESS, remarks = "Example")
        val dispatched = mutableListOf<MainAction>()
        val removals = mutableListOf<Pair<String, String>>()

        ServerMenuAction.entries.forEach { action ->
            action.perform("server-guid", profile, dispatched::add) { guid, name ->
                removals.add(guid to name)
            }
        }

        assertEquals(
            listOf(
                MainAction.ShareQRCode("server-guid"),
                MainAction.ShareClipboard("server-guid"),
                MainAction.ShareFullContent("server-guid"),
                MainAction.EditServer("server-guid", profile),
            ),
            dispatched,
        )
        assertEquals(listOf("server-guid" to "Example"), removals)
    }

    @Test
    fun duplicateNamesRetainDistinctDeletionIdentities() {
        val profile = ProfileItem(configType = EConfigType.VMESS, remarks = "Same name")
        val removals = mutableListOf<Pair<String, String>>()

        listOf("first-guid", "second-guid").forEach { guid ->
            ServerMenuAction.Delete.perform(guid, profile, { error("Deletion must request confirmation") }) { id, name ->
                removals.add(id to name)
            }
        }

        assertEquals(listOf("first-guid" to "Same name", "second-guid" to "Same name"), removals)
    }

    @Test
    fun blankNameDoesNotDiscardTheDeletionRequest() {
        val profile = ProfileItem(configType = EConfigType.VMESS, remarks = "")
        val removals = mutableListOf<Pair<String, String>>()

        ServerMenuAction.Delete.perform("server-guid", profile, { error("Deletion must request confirmation") }) { guid, name ->
            removals.add(guid to name)
        }

        assertEquals(listOf("server-guid" to ""), removals)
    }
}

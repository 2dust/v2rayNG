package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerGroupUiStateTest {
    private val profile = ProfileItem(configType = EConfigType.VLESS)
    private val initial = ServerGroupUiState(
        servers = listOf("a", "b", "c").map { ServersCache(it, profile) },
        rows = listOf("a", "b", "c").map { ServerRowUiModel(it, profile, "", "", "", 0, "") }
    )

    @Test
    fun `moves both the rendered row and persisted server by GUID after a previous move`() {
        val first = requireNotNull(initial.moveServer("a", "c"))
        assertEquals(listOf("b", "c", "a"), first.rows.map { it.guid })

        val second = requireNotNull(first.moveServer("a", "b"))
        assertEquals(listOf("a", "b", "c"), second.servers.map { it.guid })
        assertEquals(second.servers.map { it.guid }, second.rows.map { it.guid })
        assertEquals(listOf("a", "b", "c"), initial.servers.map { it.guid })
    }

    @Test
    fun `ignores moves when either profile is gone or both GUIDs are the same`() {
        assertNull(initial.moveServer("missing", "a"))
        assertNull(initial.moveServer("a", "missing"))
        assertNull(initial.moveServer("a", "a"))
        assertNull(ServerGroupUiState().moveServer("a", "b"))
    }

    @Test
    fun `waits for unfiltered rows after clearing search before allowing a move`() {
        val filtered = initial.copy(servers = initial.servers.take(2), rows = initial.rows.take(2), isFiltered = true)
        assertFalse(initial.canReorder("group", "search"))
        assertFalse(filtered.canReorder("group", ""))
        assertNull(filtered.moveServer("a", "b"))
        assertFalse(initial.canReorder("", ""))
        assertTrue(initial.canReorder("group", ""))
    }
}

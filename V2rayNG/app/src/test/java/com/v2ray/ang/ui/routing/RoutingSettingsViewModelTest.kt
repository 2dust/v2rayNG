package com.v2ray.ang.ui.routing

import android.app.Application
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RoutingSettingsViewModelTest {
    private lateinit var storage: MMKV
    private lateinit var viewModel: RoutingSettingsViewModel
    private var saved: String? = null

    @BeforeEach
    fun prepareStorage() {
        mockStatic(MMKV::class.java).use { factory ->
            factory.`when`<MMKV> { MMKV.mmkvWithID("SETTING", MMKV.MULTI_PROCESS_MODE) }.thenReturn(mock())
            // The shared lazy handle may have been initialized by an earlier storage test.
            val getter = MmkvManager::class.java.getDeclaredMethod("getSettingsStorage")
            getter.isAccessible = true
            storage = getter.invoke(MmkvManager) as MMKV
        }
        reset(storage)
        whenever(storage.decodeString(AppConfig.PREF_ROUTING_RULESET)).thenAnswer { saved }
        whenever(storage.encode(any<String>(), any<String>())).thenAnswer {
            saved = it.getArgument(1)
            true
        }
        viewModel = RoutingSettingsViewModel(mock<Application>())
        assertEquals(emptyList<RulesetItem>(), viewModel.rulesetsFlow.value)
    }

    @Test
    fun deletesTheStableTargetAfterStoredOrderChanges() = runBlocking<Unit> {
        val first = RulesetItem(id = "first", remarks = "Same")
        val second = RulesetItem(id = "second", remarks = "Same", locked = true)
        saved = JsonUtil.toJson(listOf(first, second))
        viewModel.reload()
        saved = JsonUtil.toJson(listOf(second, first))

        viewModel.remove(second.id)

        assertEquals(listOf(first), MmkvManager.decodeRoutingRulesets())
        assertEquals(listOf(first), viewModel.rulesetsFlow.value)
        viewModel.reload()
        assertEquals(listOf(first), viewModel.getAll())
    }

    @Test
    fun missingTargetDoesNotWriteOrRemoveAnotherRule() = runBlocking<Unit> {
        val rule = RulesetItem(id = "keep")
        saved = JsonUtil.toJson(listOf(rule))
        viewModel.reload()
        viewModel.remove("missing")
        assertEquals(listOf(rule), viewModel.getAll())
        verify(storage, never()).encode(any<String>(), any<String>())
    }

    @Test
    fun alreadyRemovedTargetIsDroppedFromTheVisibleList() = runBlocking<Unit> {
        saved = JsonUtil.toJson(listOf(RulesetItem(id = "gone")))
        viewModel.reload()
        saved = null
        viewModel.remove("gone")
        assertEquals(emptyList<RulesetItem>(), viewModel.getAll())
        verify(storage, never()).encode(any<String>(), any<String>())
    }

    @Test
    fun deletionOfTheLastRuleCanBeRepeated() = runBlocking<Unit> {
        saved = JsonUtil.toJson(listOf(RulesetItem(id = "last")))
        viewModel.reload()
        viewModel.remove("last")
        assertEquals("", saved)
        viewModel.remove("last")
        viewModel.reload()
        assertEquals(emptyList<RulesetItem>(), viewModel.getAll())
    }

    @Test
    fun refusedWriteDoesNotRemoveTheVisibleOrStoredRule() = runBlocking<Unit> {
        val rule = RulesetItem(id = "keep")
        saved = JsonUtil.toJson(listOf(rule))
        viewModel.reload()
        whenever(storage.encode(any<String>(), any<String>())).thenReturn(false)
        assertTrue(runCatching { viewModel.remove(rule.id) }.exceptionOrNull() is IllegalStateException)
        assertEquals(listOf(rule), viewModel.getAll())
        assertEquals(listOf(rule), MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun duplicateAndMissingLegacyIdsArePersistedBeforeDeletingOneRow() = runBlocking<Unit> {
        saved = """[{"id":"same","remarks":"Same","locked":true},{"id":"same","remarks":"Same","locked":true},{"id":null},{"id":" "}]"""
        viewModel.reload()
        val loaded = viewModel.getAll()
        assertEquals(4, loaded.map { it.id }.toSet().size)
        assertTrue(loaded.all { it.id.isNotBlank() })
        assertEquals(loaded, MmkvManager.decodeRoutingRulesets())
        viewModel.remove(loaded[1].id)
        assertEquals(listOf(loaded[0], loaded[2], loaded[3]), MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun failedIdRepairDoesNotPublishUnsavedIdentities() = runBlocking<Unit> {
        val original = """[{"id":"same"},{"id":"same"}]"""
        saved = original
        whenever(storage.encode(any<String>(), any<String>())).thenReturn(false)
        assertTrue(runCatching { viewModel.reload() }.exceptionOrNull() is IllegalStateException)
        assertEquals(original, saved)
        assertEquals(emptyList<RulesetItem>(), viewModel.getAll())
    }

    @Test
    fun reimportWithLockedDuplicatesKeepsEveryRowAddressable() = runBlocking<Unit> {
        val exported = """[{"id":"same","remarks":"Locked","locked":true},{"id":"same","remarks":"Locked","locked":true},{"remarks":"Other"}]"""
        saved = exported
        viewModel.reload()
        repeat(2) {
            val retainedIds = viewModel.getAll().filter { it.locked == true }.map { it.id }
            assertTrue(SettingsManager.resetRoutingRulesets(exported))
            viewModel.reload()
            val loaded = viewModel.getAll()
            assertEquals(retainedIds, loaded.take(retainedIds.size).map { it.id })
            assertEquals(loaded.size, loaded.map { it.id }.toSet().size)
        }
    }
}

package com.v2ray.ang.ui.routing

import android.app.Application
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RoutingSettingsViewModelTest {
    private lateinit var storage: MMKV
    private lateinit var viewModel: RoutingSettingsViewModel
    private var saved: String? = null

    @Before
    fun prepareStorage() {
        mockStatic(MMKV::class.java).use { factory ->
            factory.`when`<MMKV> { MMKV.mmkvWithID("SETTING", MMKV.MULTI_PROCESS_MODE) }.thenReturn(mock())
            // Reuse the lazy handle if another storage test initialized it first.
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
    fun deletesByIdAfterStoredOrderChangesAndKeepsDuplicateNames() = runBlocking {
        val first = RulesetItem(id = "first", remarks = "Same")
        val second = RulesetItem(id = "second", remarks = "Same", locked = true)
        saved = JsonUtil.toJson(listOf(first, second))
        viewModel.reload()
        saved = JsonUtil.toJson(listOf(second, first))

        viewModel.remove("second")

        assertEquals(listOf(first), viewModel.rulesetsFlow.value)
        assertEquals(listOf(first), MmkvManager.decodeRoutingRulesets())
        viewModel.reload()
        assertEquals(listOf(first), viewModel.getAll())
    }

    @Test
    fun missingTargetDoesNotWriteOrRemoveAnotherRule() = runBlocking<Unit> {
        val rule = RulesetItem(id = "keep")
        saved = JsonUtil.toJson(listOf(rule))
        viewModel.reload()
        viewModel.remove("missing")
        assertEquals(listOf(rule), viewModel.rulesetsFlow.value)
        verify(storage, never()).encode(any<String>(), any<String>())
    }

    @Test
    fun alreadyRemovedTargetIsDroppedFromTheVisibleList() = runBlocking<Unit> {
        saved = JsonUtil.toJson(listOf(RulesetItem(id = "gone")))
        viewModel.reload()
        saved = null
        viewModel.remove("gone")
        assertEquals(emptyList<RulesetItem>(), viewModel.rulesetsFlow.value)
        verify(storage, never()).encode(any<String>(), any<String>())
    }

    @Test
    fun deletingTheLastRulePersistsAnEmptyListAndCanBeRepeated() = runBlocking {
        saved = JsonUtil.toJson(listOf(RulesetItem(id = "last")))
        viewModel.reload()
        viewModel.remove("last")
        assertEquals("", saved)
        assertEquals(emptyList<RulesetItem>(), viewModel.rulesetsFlow.value)
        viewModel.remove("last")
        viewModel.reload()
        assertEquals(emptyList<RulesetItem>(), viewModel.getAll())
    }

    @Test
    fun storageExceptionDoesNotRemoveTheVisibleRule() = runBlocking {
        val rule = RulesetItem(id = "keep")
        saved = JsonUtil.toJson(listOf(rule))
        viewModel.reload()
        val failure = IllegalStateException("Storage unavailable")
        whenever(storage.encode(any<String>(), any<String>())).thenThrow(failure)
        try {
            viewModel.remove("keep")
            fail("Expected storage failure")
        } catch (actual: IllegalStateException) {
            assertEquals(failure.message, actual.message)
        }
        assertEquals(listOf(rule), viewModel.rulesetsFlow.value)
        assertEquals(listOf(rule), MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun legacyDuplicateIdsArePersistedBeforeDeletingOnlyTheChosenRow() = runBlocking {
        saved = """[{"id":"same","remarks":"Same","locked":true},{"id":"same","remarks":"Same","locked":true},{"id":null}]"""
        viewModel.reload()
        val loaded = viewModel.getAll()
        assertEquals(3, loaded.map { it.id }.toSet().size)
        assertTrue(loaded.all { it.id.isNotBlank() })
        assertEquals(loaded, MmkvManager.decodeRoutingRulesets())
        viewModel.remove(loaded[1].id)
        assertEquals(listOf(loaded[0], loaded[2]), MmkvManager.decodeRoutingRulesets())
        assertEquals(listOf(loaded[0], loaded[2]), viewModel.getAll())
    }

    @Test
    fun failedRepairDoesNotPublishTemporaryIdsOrOverwriteStoredRules() = runBlocking {
        val original = """[{"id":"same"},{"id":"same"}]"""
        saved = original
        whenever(storage.encode(any<String>(), any<String>())).thenReturn(false)
        assertTrue(runCatching { viewModel.reload() }.exceptionOrNull() is IllegalStateException)
        assertEquals(original, saved)
        assertEquals(emptyList<RulesetItem>(), viewModel.getAll())
        assertEquals(2, MmkvManager.decodeRoutingRulesets()?.size)
    }

    @Test
    fun refusedDeletionDoesNotRemoveTheVisibleRule() = runBlocking {
        val rule = RulesetItem(id = "keep")
        saved = JsonUtil.toJson(listOf(rule))
        viewModel.reload()
        whenever(storage.encode(any<String>(), any<String>())).thenReturn(false)
        assertTrue(runCatching { viewModel.remove("keep") }.exceptionOrNull() is IllegalStateException)
        assertEquals(listOf(rule), viewModel.getAll())
        assertEquals(listOf(rule), MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun importingAnOldExportRepeatedlyKeepsLockedCopiesAndDistinctRulesStable() = runBlocking {
        val exported = """[{"id":"same","remarks":"Locked","locked":true},{"id":"same","remarks":"Locked","locked":true},{"remarks":"Other"}]"""
        saved = exported
        viewModel.reload()
        val lockedIds = viewModel.getAll().take(2).map { it.id }
        repeat(3) {
            assertTrue(SettingsManager.resetRoutingRulesets(exported))
            viewModel.reload()
            val loaded = viewModel.getAll()
            assertEquals(3, loaded.size)
            assertEquals(lockedIds, loaded.take(2).map { it.id })
            assertEquals(3, loaded.map { it.id }.toSet().size)
        }
    }

    @Test
    fun staleReloadCannotUndoANewerReorderOrToggle() = runBlocking {
        saved = JsonUtil.toJson(listOf(RulesetItem(id = "one"), RulesetItem(id = "two")))
        viewModel.reload()
        for (toggle in listOf(false, true)) {
            val readStarted = CountDownLatch(1)
            whenever(storage.decodeString(AppConfig.PREF_ROUTING_RULESET)).thenAnswer {
                val snapshot = saved
                readStarted.countDown()
                snapshot
            }
            val pending = launch(start = CoroutineStart.UNDISPATCHED) { viewModel.reload() }
            assertTrue(readStarted.await(5, TimeUnit.SECONDS))
            if (toggle) viewModel.update(0, viewModel.getAll()[0].copy(enabled = false))
            else viewModel.move(0, 1)
            val expected = viewModel.getAll()
            pending.join()
            assertEquals(expected, viewModel.rulesetsFlow.value)
            assertEquals(expected, MmkvManager.decodeRoutingRulesets())
        }
    }

    @Test
    fun addAndOutOfRangeOperationsRetainTheirIndexBasedContract() {
        assertEquals(null, SettingsManager.getRoutingRuleset(-1))
        SettingsManager.saveRoutingRuleset(-1, RulesetItem(remarks = "New"))
        val added = SettingsManager.getRoutingRuleset(0)!!
        assertTrue(added.id.isNotBlank())
        assertEquals("New", added.remarks)
        assertEquals(null, SettingsManager.getRoutingRuleset(3))
        SettingsManager.removeRoutingRuleset(-1)
        SettingsManager.removeRoutingRuleset(3)
        assertEquals(listOf(added), MmkvManager.decodeRoutingRulesets())
    }
}

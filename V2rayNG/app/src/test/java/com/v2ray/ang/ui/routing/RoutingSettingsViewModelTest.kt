package com.v2ray.ang.ui.routing

import android.app.Application
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}

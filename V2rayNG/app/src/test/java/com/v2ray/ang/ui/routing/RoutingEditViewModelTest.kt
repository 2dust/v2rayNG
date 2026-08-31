package com.v2ray.ang.ui.routing

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseViewModelEvent
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Test-only dispatcher replacement; remove the opt-in when kotlinx-coroutines-test stabilizes it.
@OptIn(ExperimentalCoroutinesApi::class)
class RoutingEditViewModelTest {
    private val values = ConcurrentHashMap<String, String>()
    private val models = mutableListOf<RoutingEditViewModel>()
    private lateinit var log: MockedStatic<Log>

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        log = mockStatic(Log::class.java)
        reset(storage)
        whenever(storage.decodeString(any())).thenAnswer { values[it.getArgument<String>(0)] }
        whenever(storage.encode(any<String>(), any<String>())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument(1)
            true
        }
    }

    @After
    fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        log.close()
        Dispatchers.resetMain()
    }

    @Test
    fun addWithoutIdOpensEmptyEditorAndPersistsNewRule() = runTest {
        val model = model()
        assertNull(model.state.value)
        assertFalse(model.save(rule("", "Too early")))
        model.initialize(null)
        val state = model.state.filterNotNull().first()
        assertNull(state.initial)
        assertTrue(state.outboundSuggestions.contains(AppConfig.TAG_PROXY))

        assertTrue(model.save(rule("", "New rule")))
        assertEquals(BaseViewModelEvent.FinishActivity, model.viewModelEvent.first())
        val saved = MmkvManager.decodeRoutingRulesets()!!.single()
        assertEquals("New rule", saved.remarks)
        assertTrue(saved.id.isNotBlank())
    }

    @Test
    fun staleAndEmptyExplicitIdsCloseInsteadOfOpeningAddEditor() = runTest {
        for (id in listOf("missing", "")) {
            val model = model()
            model.initialize(id)
            assertEquals(BaseViewModelEvent.FinishActivity, model.viewModelEvent.first())
            assertNull(model.state.value)
        }
    }

    @Test
    fun editsCorrectIdAfterOrderChangesDespiteRepeatedTitles() = runTest {
        val first = rule("first", "Same")
        val second = rule("second", "Same")
        MmkvManager.encodeRoutingRulesets(mutableListOf(first, second))
        val model = model()
        model.initialize("second")
        val state = model.state.filterNotNull().first()
        assertEquals(second, state.initial)
        model.initialize("first")
        assertEquals(second, model.state.value!!.initial)
        MmkvManager.encodeRoutingRulesets(mutableListOf(second, first))

        model.save(second.copy(remarks = "Edited"))
        assertEquals(BaseViewModelEvent.FinishActivity, model.viewModelEvent.first())
        assertEquals(listOf(second.copy(remarks = "Edited"), first), MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun deletesOnlyRequestedIdAfterReorder() = runTest {
        val first = rule("first", "Same")
        val second = rule("second", "Same")
        MmkvManager.encodeRoutingRulesets(mutableListOf(first, second))
        val model = model()
        model.initialize("second")
        model.state.filterNotNull().first()
        MmkvManager.encodeRoutingRulesets(mutableListOf(second, first))

        model.delete()
        assertEquals(BaseViewModelEvent.FinishActivity, model.viewModelEvent.first())
        assertEquals(listOf(first), MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun missingRemarksDoNotSaveOrCloseEditor() = runTest {
        val model = model()
        model.initialize(null)
        model.state.filterNotNull().first()
        assertFalse(model.save(rule("", "")))
        assertFalse(model.save(rule("", "").copy(remarks = null)))
        assertNull(MmkvManager.decodeRoutingRulesets())
        verify(model, org.mockito.kotlin.times(2)).toast(R.string.sub_setting_remarks)
        model.delete()
        runCurrent()
        assertNull(MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun storageFailureLeavesEditorOpenForRetry() = runTest {
        val model = model()
        model.initialize(null)
        model.state.filterNotNull().first()
        whenever(storage.encode(eq(AppConfig.PREF_ROUTING_RULESET), any<String>())).thenThrow(IllegalStateException("Test write failure"))
        model.save(rule("", "Retry"))
        runCurrent()
        model.isLoading.first { !it }
        verify(model).toastError(R.string.toast_failure)
        assertNull(model.state.value!!.initial)
        assertNull(MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun loadingFailureClosesEditorAndReportsFailure() = runTest {
        whenever(storage.decodeString(AppConfig.PREF_ROUTING_RULESET)).thenThrow(IllegalStateException("Test read failure"))
        val model = model()
        model.initialize("existing")
        assertEquals(BaseViewModelEvent.FinishActivity, model.viewModelEvent.first())
        verify(model).toastError(R.string.toast_failure)
        assertNull(model.state.value)
    }

    @Test
    fun storageRepairsLegacyIdsOnceBeforePublishing() = runTest {
        values[AppConfig.PREF_ROUTING_RULESET] = """[{"id":"duplicate","remarks":"Same"},{"id":"duplicate","remarks":"Same"},{"remarks":"Legacy"}]"""
        val model = RoutingSettingsViewModel(mock<Application>())
        try {
            assertTrue(model.rulesetsFlow.value.isEmpty())
            model.reload()
            val rules = model.rulesetsFlow.first { it.isNotEmpty() }
            assertEquals(3, rules.map { it.id }.toSet().size)
            assertTrue(rules.all { it.id.isNotBlank() })
            assertEquals(rules, MmkvManager.decodeRoutingRulesets())
            assertEquals(rules, JsonUtil.fromJson(values[AppConfig.PREF_ROUTING_RULESET]!!, Array<RulesetItem>::class.java)!!.toList())
        } finally {
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun failedIdRepairIsNotPublishedAsIfItWerePersisted() = runTest {
        values[AppConfig.PREF_ROUTING_RULESET] = """[{"remarks":"Legacy"}]"""
        whenever(storage.encode(eq(AppConfig.PREF_ROUTING_RULESET), any<String>())).thenReturn(false)
        val model = spy(RoutingSettingsViewModel(mock<Application>()))
        doNothing().whenever(model).toastError(any<Int>())
        try {
            model.reload()
            runCurrent()
            model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().forEach { it.join() }
            verify(model).toastError(R.string.toast_failure)
            assertTrue(model.rulesetsFlow.value.isEmpty())
            assertEquals("""[{"remarks":"Legacy"}]""", values[AppConfig.PREF_ROUTING_RULESET])
        } finally {
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun loadingEmptyRuleListPublishesEmptyState() = runTest {
        val model = RoutingSettingsViewModel(mock<Application>())
        try {
            model.reload()
            runCurrent()
            model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().forEach { it.join() }
            assertTrue(model.rulesetsFlow.value.isEmpty())
            assertNull(MmkvManager.decodeRoutingRulesets())
        } finally {
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun pendingReloadCannotUndoToggleOrReorder() = runTest {
        for (reorder in listOf(false, true)) {
            val first = rule("first", "Same")
            val second = rule("second", "Same")
            MmkvManager.encodeRoutingRulesets(mutableListOf(first, second))
            val model = RoutingSettingsViewModel(mock<Application>())
            val release = CountDownLatch(1)
            try {
                model.reload()
                model.rulesetsFlow.first { it.isNotEmpty() }
                val started = CompletableDeferred<Unit>()
                val blockOnce = AtomicBoolean(true)
                whenever(storage.decodeString(AppConfig.PREF_ROUTING_RULESET)).thenAnswer {
                    val snapshot = values[AppConfig.PREF_ROUTING_RULESET]
                    if (blockOnce.getAndSet(false)) {
                        started.complete(Unit)
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                    snapshot
                }
                model.reload()
                started.await()
                val expected = if (reorder) {
                    model.move(0, 1)
                    listOf(second, first)
                } else {
                    model.update(0, first.copy(enabled = false))
                    listOf(first.copy(enabled = false), second)
                }
                release.countDown()
                model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().forEach { it.join() }
                assertEquals(expected, model.rulesetsFlow.value)
                assertEquals(expected, MmkvManager.decodeRoutingRulesets())
            } finally {
                release.countDown()
                model.viewModelScope.cancel()
            }
        }
    }

    private fun model(): RoutingEditViewModel = spy(RoutingEditViewModel(mock<Application>())).also {
        doNothing().whenever(it).toast(any<Int>())
        doNothing().whenever(it).toastSuccess(any<Int>())
        doNothing().whenever(it).toastError(any<Int>())
        models.add(it)
    }

    private fun rule(id: String, remarks: String) = RulesetItem(id = id, remarks = remarks, outboundTag = AppConfig.TAG_PROXY)

    companion object {
        private val storage: MMKV = mock()

        @BeforeClass
        @JvmStatic
        fun initializeStorageHandles() {
            mockStatic(MMKV::class.java).use { mmkv ->
                for (name in listOf("SETTING", "MAIN")) {
                    mmkv.`when`<MMKV> { MMKV.mmkvWithID(name, MMKV.MULTI_PROCESS_MODE) }.thenReturn(storage)
                }
                MmkvManager.decodeRoutingRulesets()
                MmkvManager.decodeAllServerList()
            }
        }
    }
}

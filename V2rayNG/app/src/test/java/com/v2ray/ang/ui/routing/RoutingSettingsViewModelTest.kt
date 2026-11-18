package com.v2ray.ang.ui.routing

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import java.io.ByteArrayInputStream
import java.io.IOException

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
    fun reimportWithLockedDuplicatesRetainsEachLockedRuleExactlyOnce() = runBlocking<Unit> {
        val exported = """[{"id":"same","remarks":"Locked","locked":true},{"id":"same","remarks":"Locked","locked":true},{"remarks":"Other"}]"""
        saved = exported
        viewModel.reload()
        repeat(2) {
            val retainedIds = viewModel.getAll().filter { it.locked == true }.map { it.id }
            assertTrue(SettingsManager.resetRoutingRulesets(exported))
            viewModel.reload()
            val loaded = viewModel.getAll()
            assertEquals(retainedIds, loaded.take(retainedIds.size).map { it.id })
            assertEquals(3, loaded.size)
            assertEquals(2, loaded.count { it.locked == true })
            assertEquals(loaded.size, loaded.map { it.id }.toSet().size)
        }
    }

    @Test
    fun importingAnEditedLockedIdentityPreservesTheSavedRuleAndOtherSameNamedRules() = runBlocking<Unit> {
        val locked = RulesetItem(id = "locked", remarks = "Same", domain = listOf("saved.example"), locked = true)
        val replacement = RulesetItem(id = "other", remarks = "Same")
        saved = JsonUtil.toJson(listOf(locked, RulesetItem(id = "old")))
        viewModel.reload()

        assertTrue(SettingsManager.resetRoutingRulesets(JsonUtil.toJson(listOf(locked.copy(locked = false), replacement))))
        viewModel.reload()

        assertEquals(listOf(locked, replacement), viewModel.getAll())
        assertEquals(listOf(locked, replacement), MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun refusedResetDoesNotPersistPartialIdentityRepairsOrReplaceVisibleRules() = runBlocking<Unit> {
        val original = """[{"id":"same","locked":true},{"id":"same","locked":true}]"""
        saved = original
        whenever(storage.encode(any<String>(), any<String>())).thenReturn(false)

        mockStatic(Log::class.java).use {
            assertFalse(SettingsManager.resetRoutingRulesets(JsonUtil.toJson(listOf(RulesetItem(id = "new")))))
        }

        assertEquals(original, saved)
        assertEquals(emptyList<RulesetItem>(), viewModel.getAll())
    }

    @Test
    fun presetResetPreservesLockedContentsAndDistinctSameNamedRules() {
        val locked = RulesetItem(id = "locked", remarks = "Same", locked = true)
        val other = RulesetItem(id = "other", remarks = "Same")
        saved = JsonUtil.toJson(listOf(locked, RulesetItem(id = "old")))
        val context = presetContext(JsonUtil.toJson(listOf(locked.copy(locked = false), other)))

        assertTrue(SettingsManager.resetRoutingRulesetsFromPresets(context, RoutingType.WHITE))

        assertEquals(listOf(locked, other), MmkvManager.decodeRoutingRulesets())
    }

    @Test
    fun refusedPresetResetReturnsFalseWithoutPartialIdentityRepairs() {
        val original = """[{"id":"same","locked":true},{"id":"same","locked":true}]"""
        saved = original
        whenever(storage.encode(any<String>(), any<String>())).thenReturn(false)
        val context = presetContext(JsonUtil.toJson(listOf(RulesetItem(id = "new"))))

        mockStatic(Log::class.java).use {
            assertFalse(SettingsManager.resetRoutingRulesetsFromPresets(context, RoutingType.WHITE))
        }

        assertEquals(original, saved)
    }

    @Test
    fun emptyOrInvalidPresetsReturnFalseWithoutReplacingSavedRules() {
        val original = JsonUtil.toJson(listOf(RulesetItem(id = "keep")))
        saved = original

        mockStatic(Log::class.java).use {
            for (content in listOf("", "[]", "null", "invalid JSON")) {
                assertFalse(SettingsManager.resetRoutingRulesetsFromPresets(presetContext(content), RoutingType.WHITE))
                assertEquals(original, saved)
            }
        }
        verify(storage, never()).encode(any<String>(), any<String>())
    }

    @Test
    fun unreadablePresetReturnsFalseWithoutWriting() {
        val context = presetContext("")
        whenever(context.assets.open(RoutingType.WHITE.fileName)).thenThrow(IOException("Asset unavailable"))

        mockStatic(Log::class.java).use {
            assertFalse(SettingsManager.resetRoutingRulesetsFromPresets(context, RoutingType.WHITE))
        }

        verify(storage, never()).encode(any<String>(), any<String>())
    }

    private fun presetContext(content: String): Context {
        val context = mock<Context>()
        val assets = mock<AssetManager>()
        whenever(context.assets).thenReturn(assets)
        whenever(assets.open(RoutingType.WHITE.fileName)).thenAnswer { ByteArrayInputStream(content.toByteArray()) }
        return context
    }
}

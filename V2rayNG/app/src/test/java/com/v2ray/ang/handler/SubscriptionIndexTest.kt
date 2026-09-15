package com.v2ray.ang.handler

import android.util.Log
import android.util.Base64
import android.webkit.URLUtil
import com.tencent.mmkv.MMKV
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.JsonUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionIndexTest {
    private val mainValues = mutableMapOf<String, String>()
    private val subValues = mutableMapOf<String, String>()

    @Before
    fun prepareStorage() {
        for ((storage, values) in listOf(main to mainValues, subs to subValues)) {
            reset(storage)
            whenever(storage.decodeString(any())).thenAnswer { values[it.getArgument<String>(0)] }
            whenever(storage.encode(any<String>(), any<String>())).thenAnswer {
                values[it.getArgument(0)] = it.getArgument(1)
                true
            }
            whenever(storage.allKeys()).thenAnswer { values.keys.toTypedArray() }
        }
    }

    @Test
    fun duplicateIdsKeepTheirFirstPositionWithoutWritingStorage() {
        val stored = """["second","first","second","third","first"]"""
        mainValues["SUB_IDS"] = stored

        assertEquals(listOf("second", "first", "third"), MmkvManager.decodeSubsList())
        assertEquals(stored, mainValues["SUB_IDS"])
        verify(main, never()).encode(any<String>(), any<String>())
    }

    @Test
    fun duplicateIndexEntriesProduceOnlyOneSubscriptionRow() {
        mainValues["SUB_IDS"] = """["b","a","b","a"]"""
        subValues["a"] = JsonUtil.toJson(SubscriptionItem(remarks = "Alpha"))
        subValues["b"] = JsonUtil.toJson(SubscriptionItem(remarks = "Beta"))

        val rows = MmkvManager.decodeSubscriptions()

        assertEquals(listOf("b", "a"), rows.map { it.guid })
        assertEquals(listOf("Beta", "Alpha"), rows.map { it.subscription.remarks })
    }

    @Test
    fun repeatedAndBlankNamesDoNotMergeDifferentSubscriptions() {
        mainValues["SUB_IDS"] = """["a","b","c","d"]"""
        listOf("a" to "Same", "b" to "Same", "c" to "", "d" to " ").forEach { (id, name) ->
            subValues[id] = JsonUtil.toJson(SubscriptionItem(remarks = name))
        }

        assertEquals(listOf("a", "b", "c", "d"), MmkvManager.decodeSubscriptions().map { it.guid })
    }

    @Test
    fun decodedIndexRemainsMutableAndCanBeSavedInANewOrder() {
        mainValues["SUB_IDS"] = """["a","b","a"]"""
        val ids = MmkvManager.decodeSubsList()
        ids.remove("a")
        ids.add(0, "c")
        MmkvManager.encodeSubsList(ids)

        assertEquals("""["c","b"]""", mainValues["SUB_IDS"])
        assertEquals(listOf("c", "b"), MmkvManager.decodeSubsList())
    }

    @Test
    fun missingBlankAndEmptyIndexesRemainEmpty() {
        assertEquals(emptyList<String>(), MmkvManager.decodeSubsList())
        listOf("", " ", "[]", "null").forEach { stored ->
            mainValues["SUB_IDS"] = stored
            assertEquals(emptyList<String>(), MmkvManager.decodeSubsList())
        }
    }

    @Test
    fun malformedIndexKeepsTheExistingEmptyFallback() {
        mockStatic(Log::class.java).use {
            mainValues["SUB_IDS"] = "{"
            assertEquals(emptyList<String>(), MmkvManager.decodeSubsList())
        }
    }

    @Test
    fun addingEleventhSubscriptionOnlyFetchesTheNewSubscription() = withImporter { importer, fetched ->
        seedSubscriptions(10)
        val before = subValues.toMap()

        assertEquals(0 to 1, importer.importBatchConfig("https://example.invalid/new#New", "", true))

        val created = MmkvManager.decodeSubscriptions().single { it.subscription.remarks == "New" }
        assertEquals(listOf(created), fetched)
        assertTrue(created.guid.isNotBlank())
        before.forEach { (id, value) -> assertEquals(value, subValues[id]) }
        verify(importer, never()).updateConfigViaSubAll()
    }

    @Test
    fun batchImportFetchesEachNewUrlOnceAndKeepsNaming() = withImporter { importer, fetched ->
        seedSubscriptions(1)
        val urls = listOf(
            "https://example.invalid/old0", "https://example.invalid/a#First%20sub",
            "https://example.invalid/b", "https://example.invalid/a#First%20sub"
        )

        assertEquals(0 to 2, importer.importBatchConfig(urls.joinToString("\n"), "", true))

        assertEquals(listOf(urls[1], urls[2]), fetched.map { it.subscription.url })
        assertEquals(listOf("First sub", "import sub"), fetched.map { it.subscription.remarks })
        assertEquals(3, MmkvManager.decodeSubscriptions().size)
        verify(importer, never()).updateConfigViaSubAll()
    }

    @Test
    fun base64SubscriptionBatchAlsoOnlyFetchesNewUrls() = withImporter { importer, fetched ->
        seedSubscriptions(2)
        val text = "https://example.invalid/old0\nhttps://example.invalid/new"
        val encoded = java.util.Base64.getEncoder().encodeToString(text.toByteArray())

        assertEquals(0 to 1, importer.importBatchConfig(encoded, "", true))
        assertEquals(listOf("https://example.invalid/new"), fetched.map { it.subscription.url })
        verify(importer, never()).updateConfigViaSubAll()
    }

    @Test
    fun duplicateEmptyAndInvalidImportsDoNotFetchSubscriptions() = withImporter { importer, fetched ->
        seedSubscriptions(1)
        val before = subValues.toMap()

        listOf(null, "", "invalid", "https://example.invalid/old0").forEach {
            assertEquals(0 to 0, importer.importBatchConfig(it, "", true))
        }

        assertTrue(fetched.isEmpty())
        assertEquals(before, subValues)
        verify(importer, never()).updateConfigViaSubAll()
    }

    @Test
    fun failedInitialDownloadDoesNotRefreshExistingSubscriptionsOrLoseTheNewOne() =
        withImporter(SubscriptionUpdateResult(failureCount = 1)) { importer, fetched ->
            seedSubscriptions(2)

            assertEquals(0 to 1, importer.importBatchConfig("https://example.invalid/new", "", true))
            assertEquals(listOf("https://example.invalid/new"), fetched.map { it.subscription.url })
            assertEquals(fetched.single().subscription, MmkvManager.decodeSubscription(fetched.single().guid))
            verify(importer, never()).updateConfigViaSubAll()
        }

    private fun seedSubscriptions(count: Int) {
        repeat(count) {
            MmkvManager.encodeSubscription("old-$it", SubscriptionItem("Old $it", "https://example.invalid/old$it"))
        }
    }

    private fun withImporter(
        result: SubscriptionUpdateResult = SubscriptionUpdateResult(successCount = 1),
        block: (AngConfigManager, MutableList<SubscriptionCache>) -> Unit
    ) {
        mockStatic(Log::class.java).use {
            mockStatic(URLUtil::class.java).use { urls ->
                urls.`when`<Boolean> { URLUtil.isHttpsUrl(any()) }
                    .thenAnswer { it.getArgument<String>(0).startsWith("https://") }
                mockStatic(Base64::class.java).use { base64 ->
                    base64.`when`<ByteArray> { Base64.decode(any<String>(), any()) }.thenAnswer {
                        val decoder = if (it.getArgument<Int>(1) and Base64.URL_SAFE != 0) {
                            java.util.Base64.getUrlDecoder()
                        } else java.util.Base64.getDecoder()
                        decoder.decode(it.getArgument<String>(0))
                    }
                    val importer = spy(AngConfigManager)
                    val fetched = mutableListOf<SubscriptionCache>()
                    doAnswer {
                        fetched += it.getArgument<SubscriptionCache>(0)
                        result
                    }.whenever(importer).updateConfigViaSub(any())
                    block(importer, fetched)
                }
            }
        }
    }

    companion object {
        private val main: MMKV = mock()
        private val subs: MMKV = mock()
        private val settings: MMKV = mock()

        @BeforeClass
        @JvmStatic
        fun initializeHandles() {
            mockStatic(MMKV::class.java).use {
                it.`when`<MMKV> { MMKV.mmkvWithID("MAIN", MMKV.MULTI_PROCESS_MODE) }.thenReturn(main)
                it.`when`<MMKV> { MMKV.mmkvWithID("SUB", MMKV.MULTI_PROCESS_MODE) }.thenReturn(subs)
                it.`when`<MMKV> { MMKV.mmkvWithID("SETTING", MMKV.MULTI_PROCESS_MODE) }.thenReturn(settings)
                MmkvManager.decodeSubscriptions()
                MmkvManager.decodeSettingsString("test-initialize")
            }
        }
    }
}

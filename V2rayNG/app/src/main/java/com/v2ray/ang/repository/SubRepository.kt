package com.v2ray.ang.repository

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.SubUpdateOptions
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

open class SubRepository(private val app: Application) : BaseRepository() {

    // ---- Read ----

    open suspend fun loadSubscriptions(): List<SubscriptionCache> = withIO {
        MmkvManager.decodeSubscriptions()
    }

    open suspend fun loadSubscription(subId: String): SubscriptionItem? = withIO {
        MmkvManager.decodeSubscription(subId)
    }

    open suspend fun profileOptions(): List<String> = withIO {
        SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(
                EConfigType.CUSTOM,
                EConfigType.POLICYGROUP,
                EConfigType.PROXYCHAIN
            )
        )
    }

    open fun confirmRemove(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)

    open suspend fun loadUpdateOptions(): SubUpdateOptions = withIO {
        SubUpdateOptions(
            updateSubscription = MmkvManager.decodeSettingsBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false),
            autoTestAfterUpdate = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false),
            autoRemoveInvalid = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false),
            autoSortAfterTest = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)
        )
    }

    // ---- Write ----

    open suspend fun save(subId: String, item: SubscriptionItem) = withIO {
        MmkvManager.encodeSubscription(subId, item)
        SubscriptionUpdater.syncOne(subId = subId)
        SettingsChangeManager.makeSetupGroupTab()
    }

    open suspend fun updateItem(subId: String, item: SubscriptionItem) = withIO {
        MmkvManager.encodeSubscription(subId, item)
    }

    open suspend fun remove(subId: String) = withIO {
        SettingsManager.removeSubscriptionWithDefault(subId)
        SettingsChangeManager.makeSetupGroupTab()
    }

    open suspend fun saveOrder(guids: List<String>) = withIO {
        MmkvManager.encodeSubsList(guids.toMutableList())
        SettingsChangeManager.makeSetupGroupTab()
    }

    open suspend fun saveUpdateOptions(options: SubUpdateOptions) = withIO {
        MmkvManager.encodeSettings(AppConfig.PREF_UPDATE_SUBSCRIPTION, options.updateSubscription)
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, options.autoTestAfterUpdate)
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, options.autoRemoveInvalid)
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_SORT_AFTER_TEST, options.autoSortAfterTest)
    }

    // ---- Update ----

    open suspend fun updateAll(
        onProgress: suspend (done: Int, total: Int) -> Unit
    ): SubscriptionUpdateResult = withIO {
        val subs = MmkvManager.decodeSubscriptions()
        var acc = SubscriptionUpdateResult()
        onProgress(0, subs.size)
        subs.forEachIndexed { index, cache ->
            currentCoroutineContext().ensureActive()
            acc += AngConfigManager.updateConfigViaSub(cache)
            onProgress(index + 1, subs.size)
        }
        acc
    }

    open suspend fun updateInBackground(): Boolean = withIO {
        SettingsChangeManager.makeSetupGroupTab()
        val subIds = MmkvManager.decodeSubscriptions()
            .filter { it.subscription.enabled && it.subscription.url.isNotEmpty() }
            .map { it.guid }
        if (subIds.isEmpty()) return@withIO false
        MessageHelper.sendMsg2SubscriptionService(
            app,
            SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, false, subIds)
        )
        true
    }

    // ---- Share ----

    open suspend fun createQrCode(url: String): Bitmap? = withIO {
        QRCodeDecoder.createQRCode(url)
    }

    open suspend fun copyToClipboard(text: String): Boolean = runCatching {
        val manager = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(null, text))
    }.isSuccess
}

package com.dalulong.app.ui.main

import com.dalulong.app.dto.SubscriptionUpdateResult
import com.dalulong.app.dto.TestServiceMessage
import com.dalulong.app.dto.entities.ProfileItem
import com.dalulong.app.dto.entities.ServerAffiliationInfo
import com.dalulong.app.dto.entities.SubscriptionCache
import com.dalulong.app.dto.entities.SubscriptionItem
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface MainDataSource : Closeable {
    val mainServiceEvent: Flow<MainServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun setSelectedSubscriptionId(id: String)

    fun getSelectServer(): String?
    fun setSelectServer(guid: String)

    fun getConfirmRemove(): Boolean
    fun getDoubleColumnDisplay(): Boolean
    fun getAutoRemoveInvalidAfterTest(): Boolean
    fun getAutoSortAfterTest(): Boolean
    fun isGroupAllDisplayEnabled(): Boolean

    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String

    fun getSubscriptions(): List<SubscriptionCache>
    fun getSubscriptionItem(id: String): SubscriptionItem?

    fun getServerGuidList(groupId: String): List<String>
    fun decodeServerConfig(guid: String): ProfileItem?
    fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo?

    fun encodeServerList(guids: List<String>, groupId: String)

    fun removeServer(guid: String)
    fun removeAllServer(): Int
    fun removeInvalidServerByGuid(guid: String): Int
    fun removeInvalidServersInGroup(groupId: String): Int

    fun clearAllTestDelayResults(guids: List<String>)
    fun sortByTestResultsForSub(subId: String)
    fun getSubsList(): List<String>

    suspend fun importBatchConfig(
        server: String?,
        subscriptionId: String,
        updateUI: Boolean
    ): Pair<Int, Int>

    fun updateConfigViaSubAll(): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult

    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int
    fun share2QRCode(guid: String): android.graphics.Bitmap?
    fun share2Clipboard(guid: String): Boolean

    fun sendMsg2Service(msgId: Int, content: String)
    fun sendMsg2TestService(msg: TestServiceMessage)
    fun cancelAllPing()
    fun testCurrentServerRealPing()

    fun syncSubscriptions()
    fun initAssets()
}

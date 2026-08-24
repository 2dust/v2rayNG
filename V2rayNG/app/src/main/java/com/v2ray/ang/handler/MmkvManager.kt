package com.v2ray.ang.handler

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVHandler
import com.tencent.mmkv.MMKVLogLevel
import com.tencent.mmkv.MMKVRecoverStrategic
import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.AppConfig.PREF_IS_BOOTED
import com.v2ray.ang.AppConfig.PREF_ROUTING_RULESET
import com.v2ray.ang.AppConfig.TAG
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

internal open class ProfileStorageException(message: String) : IllegalStateException(message)

internal class SubscriptionUpdateAbortedException :
    ProfileStorageException("Subscription changed while its update was running")

internal class StorageMutationTransaction {
    private data class RollbackAction(
        val restore: () -> Boolean,
        val failureMessage: String,
    )

    private val rollbackActions = mutableListOf<RollbackAction>()

    fun mutate(
        change: () -> Boolean,
        restore: () -> Boolean,
        failureMessage: String,
    ) {
        rollbackActions.add(RollbackAction(restore, failureMessage))
        if (!change()) throw ProfileStorageException(failureMessage)
    }

    fun rollback(failure: Throwable) {
        rollbackActions.asReversed().forEach { action ->
            try {
                if (!action.restore()) {
                    failure.addSuppressed(
                        ProfileStorageException("Rollback failed: ${action.failureMessage}"),
                    )
                }
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
        }
    }
}

internal fun <T> runStorageMutationTransaction(
    block: StorageMutationTransaction.() -> T,
): T {
    val transaction = StorageMutationTransaction()
    return try {
        transaction.block()
    } catch (failure: Throwable) {
        transaction.rollback(failure)
        throw failure
    }
}

object MmkvManager {

    //region private

    private const val ID_MAIN = "MAIN"
    private const val ID_PROFILE_FULL_CONFIG = "PROFILE_FULL_CONFIG"
    private const val ID_SERVER_RAW = "SERVER_RAW"
    private const val ID_SERVER_AFF = "SERVER_AFF"
    private const val ID_SUB = "SUB"
    private const val ID_ASSET = "ASSET"
    private const val ID_SETTING = "SETTING"
    private const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    private const val KEY_ANG_CONFIGS = "ANG_CONFIGS"
    private const val KEY_SUB_SERVER_PREFIX = "SUB_SERVERS_"
    private const val KEY_SUB_IDS = "SUB_IDS"
    private const val KEY_WEBDAV_CONFIG = "WEBDAV_CONFIG"

    private val recoveryHandler = object : MMKVHandler {
        override fun onMMKVCRCCheckFail(mmapID: String) =
            recoverFromStorageError(mmapID, "CRC check")

        override fun onMMKVFileLengthError(mmapID: String) =
            recoverFromStorageError(mmapID, "file length check")

        override fun wantLogRedirecting(): Boolean = false

        override fun mmkvLog(
            level: MMKVLogLevel,
            file: String,
            line: Int,
            function: String,
            message: String
        ) = Unit
    }

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val profileFullStorage by lazy { MMKV.mmkvWithID(ID_PROFILE_FULL_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID(ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val assetStorage by lazy { MMKV.mmkvWithID(ID_ASSET, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

    private inline fun <T> withProfileIndexLock(block: () -> T): T {
        return synchronized(mainStorage) {
            mainStorage.lock()
            try {
                block()
            } finally {
                mainStorage.unlock()
            }
        }
    }

    private fun removeProfilePayloads(guids: Collection<String>) {
        if (guids.isEmpty()) return
        val keys = guids.toTypedArray()
        profileFullStorage.removeValuesForKeys(keys)
        serverAffStorage.removeValuesForKeys(keys)
        serverRawStorage.removeValuesForKeys(keys)
    }

    private fun readStoredString(storage: MMKV, key: String, description: String): String? {
        if (!storage.containsKey(key)) return null
        return storage.decodeString(key)
            ?: throw ProfileStorageException("Failed to read $description")
    }

    private fun restoreStoredString(storage: MMKV, key: String, stored: String?): Boolean {
        return if (stored != null) {
            storage.encode(key, stored)
        } else {
            storage.remove(key)
            !storage.containsKey(key)
        }
    }

    private fun StorageMutationTransaction.writeString(
        storage: MMKV,
        key: String,
        value: String,
        description: String,
    ) {
        val stored = readStoredString(storage, key, description)
        if (stored == value) return
        mutate(
            change = { storage.encode(key, value) },
            restore = { restoreStoredString(storage, key, stored) },
            failureMessage = "Failed to write $description",
        )
    }

    private fun StorageMutationTransaction.removeString(
        storage: MMKV,
        key: String,
        description: String,
    ) {
        val stored = readStoredString(storage, key, description)
        if (stored == null) return
        mutate(
            change = {
                storage.remove(key)
                !storage.containsKey(key)
            },
            restore = { restoreStoredString(storage, key, stored) },
            failureMessage = "Failed to remove $description",
        )
    }

    private fun StorageMutationTransaction.writeProfilePayload(guid: String, profile: ProfileItem) {
        writeString(profileFullStorage, guid, JsonUtil.toJson(profile), "profile payload")
    }

    private fun StorageMutationTransaction.writeRawProfilePayload(guid: String, raw: String) {
        writeString(serverRawStorage, guid, raw, "raw profile payload")
    }

    private fun StorageMutationTransaction.writeProfileIndex(
        profileIndexKey: String,
        serverList: List<String>,
    ) {
        writeString(mainStorage, profileIndexKey, JsonUtil.toJson(serverList), "profile index")
    }

    private fun StorageMutationTransaction.writeSelectedProfile(guid: String) {
        writeString(mainStorage, KEY_SELECTED_SERVER, guid, "selected profile")
    }

    private fun StorageMutationTransaction.removeSelectedProfile() {
        removeString(mainStorage, KEY_SELECTED_SERVER, "selected profile")
    }

    private fun StorageMutationTransaction.writeSubscriptionPayload(
        guid: String,
        subscription: SubscriptionItem,
    ) {
        writeString(subStorage, guid, JsonUtil.toJson(subscription), "subscription payload")
    }

    private fun StorageMutationTransaction.writeSubscriptionIndex(subsList: List<String>) {
        writeString(mainStorage, KEY_SUB_IDS, JsonUtil.toJson(subsList), "subscription index")
    }

    private fun decodeStringListForWrite(key: String, description: String): MutableList<String> {
        val stored = readStoredString(mainStorage, key, description) ?: return mutableListOf()
        if (stored.isBlank()) throw ProfileStorageException("Failed to decode $description")
        return JsonUtil.fromJsonSafe(stored, Array<String>::class.java)?.toMutableList()
            ?: throw ProfileStorageException("Failed to decode $description")
    }

    private fun decodeServerListForWrite(subscriptionId: String): MutableList<String> {
        return decodeStringListForWrite(serverListKey(subscriptionId), "profile index")
    }

    private fun decodeSubsListForWrite(): MutableList<String> {
        return decodeStringListForWrite(KEY_SUB_IDS, "subscription index")
    }

    private fun serverListKey(subscriptionId: String): String {
        return "$KEY_SUB_SERVER_PREFIX${getSubscriptionId(subscriptionId)}"
    }

    /**
     * Returns every server referenced outside the target group, or null if the raw indexes
     * cannot provide a complete view.
     */
    private fun decodeServersReferencedByOtherGroups(subscriptionId: String): Set<String>? {
        val targetKey = serverListKey(subscriptionId)
        val keys = mainStorage.allKeys() ?: return null
        if (targetKey !in keys) return null

        val referencedServers = mutableSetOf<String>()
        for (key in keys) {
            if (!key.startsWith(KEY_SUB_SERVER_PREFIX) || key == targetKey) continue

            val json = mainStorage.decodeString(key)
            if (json.isNullOrBlank()) return null
            val serverIds = JsonUtil.fromJsonSafe(json, Array<String>::class.java) ?: return null
            referencedServers.addAll(serverIds)
        }
        return referencedServers
    }

    private fun StorageMutationTransaction.removeProfilesFromGroup(
        subscriptionId: String,
        requestedGuids: Set<String>?,
    ): Set<String> {
        val serverList = decodeServerListForWrite(subscriptionId)
        val removedServers = if (requestedGuids == null) {
            serverList.toSet()
        } else {
            serverList.filterTo(linkedSetOf()) { it in requestedGuids }
        }
        if (removedServers.isEmpty()) return emptySet()

        val referencedByOtherGroups = decodeServersReferencedByOtherGroups(subscriptionId)
        val removablePayloads = ProfileReplacement.findRemovablePayloads(
            replacedServers = removedServers,
            replacementServers = emptySet(),
            protectedServer = null,
            serversReferencedByOtherGroups = referencedByOtherGroups,
        )
        val selectedServer = readStoredString(mainStorage, KEY_SELECTED_SERVER, "selected profile")
        if (selectedServer != null && selectedServer in removablePayloads) {
            removeSelectedProfile()
        }
        serverList.removeAll(removedServers)
        writeProfileIndex(serverListKey(subscriptionId), serverList)
        return removablePayloads
    }

    private fun removeProfiles(
        requestedGuids: Set<String>?,
        failureMessage: String,
        resolveSubscriptionId: () -> String,
    ): Boolean {
        return try {
            withProfileIndexLock {
                val removablePayloads = runStorageMutationTransaction {
                    removeProfilesFromGroup(resolveSubscriptionId(), requestedGuids)
                }
                removeProfilePayloads(removablePayloads)
            }
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, failureMessage, e)
            false
        }
    }

    //endregion

    /**
     * Initializes MMKV with best-effort recovery so a damaged store is not silently discarded.
     */
    fun initialize(context: Context) {
        val logLevel = if (BuildConfig.DEBUG) {
            MMKVLogLevel.LevelDebug
        } else {
            MMKVLogLevel.LevelInfo
        }
        MMKV.initialize(
            context,
            context.filesDir.resolve("mmkv").absolutePath,
            null,
            logLevel,
            recoveryHandler
        )
    }

    private fun recoverFromStorageError(mmapID: String, error: String): MMKVRecoverStrategic {
        Log.e(TAG, "MMKV $error failed for $mmapID; attempting data recovery")
        return MMKVRecoverStrategic.OnErrorRecover
    }

    //region Server

    /**
     * Reads the legacy server list from KEY_ANG_CONFIGS for migration.
     * This method is for migration purposes only.
     *
     * @return The JSON string of legacy server list, or null if not exists.
     */
    fun readLegacyServerList(): String? {
        return mainStorage.decodeString(KEY_ANG_CONFIGS)
    }


    /**
     * Gets the selected server GUID.
     *
     * @return The selected server GUID.
     */
    fun getSelectServer(): String? {
        return mainStorage.decodeString(KEY_SELECTED_SERVER)
    }

    /**
     * Sets the selected server GUID.
     *
     * @param guid The server GUID.
     */
    fun setSelectServer(guid: String) {
        withProfileIndexLock {
            mainStorage.encode(KEY_SELECTED_SERVER, guid)
        }
    }

    /**
     * Encodes the server list for a given subscription.
     * Saves to the subscription's serverList (including default subscription for ungrouped servers).
     *
     * @param serverList The list of server GUIDs.
     * @param subscriptionId The subscription ID.
     */
    fun encodeServerList(serverList: MutableList<String>, subscriptionId: String): Boolean {
        return try {
            withProfileIndexLock {
                runStorageMutationTransaction {
                    writeProfileIndex(serverListKey(subscriptionId), serverList)
                }
            }
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to persist profile order for group $subscriptionId", e)
            false
        }
    }


    /**
     * Decodes the server list for a given subscription.
     * If subscriptionId is empty, returns ungrouped servers.
     * Otherwise, returns servers from the specified subscription's serverList.
     *
     * @param subscriptionId The subscription ID.
     * @return The list of server GUIDs.
     */
    fun decodeServerList(subscriptionId: String): MutableList<String> {
        val json = mainStorage.decodeString(serverListKey(subscriptionId))
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
        }
    }

    /**
     * Decodes all server list (merged from all subscriptions including default subscription).
     * Use this when you need the complete server list.
     *
     * @return The list of all server GUIDs.
     */
    fun decodeAllServerList(): MutableList<String> {
        val allServers = mutableListOf<String>()
        val subsList = decodeSubsList()

        // If DEFAULT_SUBSCRIPTION_ID is not in the subscriptions list, add its servers
        if (!subsList.contains(DEFAULT_SUBSCRIPTION_ID)) {
            allServers.addAll(decodeServerList(DEFAULT_SUBSCRIPTION_ID))
        }

        // Add servers from all subscriptions
        subsList.forEach { guid ->
            allServers.addAll(decodeServerList(guid))
        }

        return allServers
    }


    /**
     * Decodes the server configuration.
     *
     * @param guid The server GUID.
     * @return The server configuration.
     */
    fun decodeServerConfig(guid: String): ProfileItem? {
        if (guid.isBlank()) {
            return null
        }
        val json = profileFullStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ProfileItem::class.java)
    }


    /**
     * Encodes the server configuration.
     *
     * @param guid The server GUID.
     * @param config The server configuration.
     * @return The server GUID.
     */
    fun encodeServerConfig(
        guid: String,
        config: ProfileItem,
        rawConfig: String? = null,
    ): String? {
        val key = guid.ifBlank { Utils.getUuid() }
        return try {
            withProfileIndexLock {
                // Decode every index before changing a payload so unreadable state fails closed.
                val subId = getSubscriptionId(config.subscriptionId)
                val serverList = decodeServerListForWrite(subId)
                val needsIndexWrite = !serverList.contains(key)
                val selectedServer = if (needsIndexWrite) {
                    readStoredString(mainStorage, KEY_SELECTED_SERVER, "selected profile")
                } else {
                    null
                }

                runStorageMutationTransaction {
                    rawConfig?.let { writeRawProfilePayload(key, it) }
                    writeProfilePayload(key, config)
                    if (needsIndexWrite && selectedServer.isNullOrBlank()) {
                        writeSelectedProfile(key)
                    }
                    if (needsIndexWrite) {
                        serverList.add(0, key)
                        writeProfileIndex(serverListKey(subId), serverList)
                    }
                }
            }
            key
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to persist profile $key", e)
            null
        }
    }

    /**
     * Saves a profile batch before publishing its group index and removing replaced payloads.
     *
     * @param profiles Generated GUIDs and parsed profiles, in insertion order.
     * @param rawConfigs Optional raw configuration payloads keyed by profile GUID.
     * @param subscriptionId The destination subscription ID.
     * @param append Whether to append to the existing group index.
     */
    internal fun saveServerProfiles(
        profiles: Map<String, ProfileItem>,
        rawConfigs: Map<String, String>,
        subscriptionId: String,
        append: Boolean,
        subscriptionUpdate: SubscriptionUpdateCommit? = null,
    ) {
        if (profiles.isEmpty()) return

        withProfileIndexLock {
            subscriptionUpdate?.let { update ->
                val subscriptionIds = decodeSubsListForWrite()
                val currentSubscription = decodeSubscription(subscriptionId)
                if (!SubscriptionUpdateGuard.canCommit(
                        isIndexed = subscriptionId in subscriptionIds,
                        current = currentSubscription,
                        expected = update.expected,
                    )
                ) {
                    throw SubscriptionUpdateAbortedException()
                }
            }
            val replacedServers = if (append) {
                emptyList()
            } else {
                decodeServerListForWrite(subscriptionId).toList()
            }
            val previousSelection = readStoredString(mainStorage, KEY_SELECTED_SERVER, "selected profile")
            val selectedProfile = if (!append &&
                previousSelection != null &&
                previousSelection in replacedServers
            ) {
                decodeServerConfig(previousSelection)
            } else {
                null
            }
            val replacementSelection = ProfileReplacement.findSelectedReplacement(
                profiles = profiles,
                currentSelection = previousSelection,
                selectedProfile = selectedProfile,
            )

            val serverList = if (append) {
                decodeServerListForWrite(subscriptionId)
            } else {
                mutableListOf()
            }
            val indexedServers = serverList.toHashSet()
            profiles.keys.forEach { guid ->
                if (indexedServers.add(guid)) {
                    serverList.add(0, guid)
                }
            }

            val removablePayloads = runStorageMutationTransaction {
                rawConfigs.forEach { (guid, raw) -> writeRawProfilePayload(guid, raw) }
                profiles.forEach { (guid, profile) -> writeProfilePayload(guid, profile) }
                subscriptionUpdate?.let { writeSubscriptionPayload(subscriptionId, it.replacement) }
                replacementSelection?.let { writeSelectedProfile(it) }

                val removable = if (replacedServers.isEmpty()) {
                    emptySet()
                } else {
                    ProfileReplacement.findRemovablePayloads(
                        replacedServers = replacedServers,
                        replacementServers = profiles.keys,
                        protectedServer = replacementSelection ?: previousSelection,
                        serversReferencedByOtherGroups =
                            decodeServersReferencedByOtherGroups(subscriptionId),
                    )
                }
                writeProfileIndex(serverListKey(subscriptionId), serverList)
                removable
            }
            removeProfilePayloads(removablePayloads)
        }
    }

    /**
     * Removes the server configuration.
     *
     * @param guid The server GUID.
     */
    fun removeServer(guid: String): Boolean {
        if (guid.isBlank()) return false
        return removeProfiles(setOf(guid), "Failed to remove profile $guid") {
            getSubscriptionId(decodeServerConfig(guid)?.subscriptionId)
        }
    }

    /**
     * Removes the server configurations via subscription ID.
     *
     * @param subscriptionId The subscription ID.
     */
    fun removeServerViaSubid(subscriptionId: String?): Boolean {
        val subId = getSubscriptionId(subscriptionId)
        return removeProfiles(null, "Failed to remove profiles for group $subId") { subId }
    }

    /**
     * Removes multiple server configurations from a subscription.
     *
     * @param guids The list of server GUIDs.
     * @param subscriptionId The subscription ID.
     */
    fun removeServers(guids: List<String>, subscriptionId: String): Boolean {
        if (guids.isEmpty()) return true
        val subId = getSubscriptionId(subscriptionId)
        return removeProfiles(guids.toSet(), "Failed to remove profiles for group $subId") { subId }
    }

    /**
     * Decodes the server affiliation information.
     *
     * @param guid The server GUID.
     * @return The server affiliation information.
     */
    fun decodeServerAffiliationInfo(guid: String): ServerAffiliationInfo? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverAffStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ServerAffiliationInfo::class.java)
    }

    /**
     * Encodes the server test delay in milliseconds.
     *
     * @param guid The server GUID.
     * @param testResult The test delay in milliseconds.
     */
    fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.testDelayMillis = testResult
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }

    /**
     * Clears all test delay results.
     *
     * @param keys The list of server GUIDs.
     */
    fun clearAllTestDelayResults(keys: List<String>?) {
        keys?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage.encode(key, JsonUtil.toJson(aff))
            }
        }
    }

    /**
     * Removes all server configurations.
     *
     * @return The number of server configurations removed.
     */
    fun removeAllServer(): Int {
        return withProfileIndexLock {
            val count = profileFullStorage.allKeys()?.count() ?: 0
            val profileIndexKeys = mainStorage.allKeys().orEmpty()
                .filter { key -> key.startsWith(KEY_SUB_SERVER_PREFIX) }
            runStorageMutationTransaction {
                profileIndexKeys.forEach { key ->
                    writeProfileIndex(key, emptyList())
                }
                removeSelectedProfile()
            }

            profileFullStorage.clearAll()
            serverAffStorage.clearAll()
            serverRawStorage.clearAll()
            count
        }
    }

    /**
     * Removes invalid server configurations.
     *
     * @param guid The server GUID.
     * @return The number of server configurations removed.
     */
    fun removeInvalidServer(guid: String): Int {
        var count = 0
        if (guid.isNotEmpty()) {
            decodeServerAffiliationInfo(guid)?.let { aff ->
                if (aff.testDelayMillis < 0L) {
                    if (removeServer(guid)) count++
                }
            }
        } else {
            serverAffStorage.allKeys()?.forEach { key ->
                decodeServerAffiliationInfo(key)?.let { aff ->
                    if (aff.testDelayMillis < 0L) {
                        if (removeServer(key)) count++
                    }
                }
            }
        }
        return count
    }

    /**
     * Decodes the raw server configuration.
     *
     * @param guid The server GUID.
     * @return The raw server configuration.
     */
    fun decodeServerRaw(guid: String): String? {
        return serverRawStorage.decodeString(guid)
    }

    /**
     * Removes profile payloads that are provably absent from their raw SUB_SERVERS_* index.
     *
     * SUB_IDS and SUB are intentionally ignored: either store can be missing after MMKV
     * recovery while the group indexes still identify live profiles. If any group index or
     * profile payload needed for a decision is unreadable, that data is preserved.
     *
     * @return The number of profile payloads removed, or null if cleanup could not run safely.
     */
    internal fun removeOrphanedServerProfiles(): Int? = synchronized(mainStorage) {
        mainStorage.lock()
        try {
            val indexedServersBySubscription = mainStorage.allKeys().orEmpty()
                .asSequence()
                .filter { key -> key.startsWith(KEY_SUB_SERVER_PREFIX) }
                .associate { key ->
                    val subscriptionId = key.removePrefix(KEY_SUB_SERVER_PREFIX)
                    val json = mainStorage.decodeString(key)
                    val serverIds = if (json.isNullOrBlank()) {
                        null
                    } else {
                        JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toSet()
                    }
                    subscriptionId to serverIds
                }

            val profiles = profileFullStorage.allKeys().orEmpty().map { guid ->
                StoredProfileReference(
                    guid = guid,
                    subscriptionId = decodeServerConfig(guid)?.subscriptionId,
                )
            }
            val orphans = OrphanProfileCleaner.findOrphans(
                profiles = profiles,
                indexedServersBySubscription = indexedServersBySubscription,
                selectedServer = getSelectServer(),
            ) ?: return@synchronized null

            if (orphans.isNotEmpty()) {
                val keys = orphans.toTypedArray()
                profileFullStorage.removeValuesForKeys(keys)
                serverAffStorage.removeValuesForKeys(keys)
                serverRawStorage.removeValuesForKeys(keys)
            }
            orphans.size
        } finally {
            mainStorage.unlock()
        }
    }

    //endregion

    //region Subscriptions

    private fun getSubscriptionId(subscriptionId: String?): String {
        return subscriptionId?.ifEmpty { DEFAULT_SUBSCRIPTION_ID } ?: DEFAULT_SUBSCRIPTION_ID
    }

    /**
     * Initializes the subscription list.
     */
    private fun initSubsList() {
        val subsList = decodeSubsList()
        if (subsList.isNotEmpty()) {
            return
        }
        subStorage.allKeys()?.forEach { key ->
            subsList.add(key)
        }
        encodeSubsList(subsList)
    }

    /**
     * Decodes the subscriptions.
     *
     * @return The list of subscriptions.
     */
    fun decodeSubscriptions(): List<SubscriptionCache> {
        initSubsList()

        val subscriptions = mutableListOf<SubscriptionCache>()
        decodeSubsList().forEach { key ->
            val json = subStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java) ?: SubscriptionItem()
                subscriptions.add(SubscriptionCache(key, item))
            }
        }
        return subscriptions
    }

    /**
     * Removes the subscription.
     *
     * @param subid The subscription ID.
     */
    fun removeSubscription(subid: String): Boolean {
        return try {
            withProfileIndexLock {
                val subsList = decodeSubsListForWrite()
                runStorageMutationTransaction {
                    if (subsList.remove(subid)) {
                        // Unpublish first. A process stop after this write can leave only an
                        // unreachable payload, and every in-flight update guard rejects the ID.
                        writeSubscriptionIndex(subsList)
                    }
                }

                try {
                    subStorage.remove(subid)
                    if (subStorage.containsKey(subid)) {
                        LogUtil.e(TAG, "Failed to clean payload for deleted subscription $subid")
                    }
                } catch (e: Exception) {
                    // The authoritative index no longer exposes this payload. Retain the orphan
                    // for later cleanup instead of making a completed deletion appear to fail.
                    LogUtil.e(TAG, "Failed to clean payload for deleted subscription $subid", e)
                }

                try {
                    val removablePayloads = runStorageMutationTransaction {
                        removeProfilesFromGroup(subid, requestedGuids = null)
                    }
                    removeProfilePayloads(removablePayloads)
                } catch (e: Exception) {
                    // The subscription is already unpublished. Retaining orphaned payloads is
                    // safer than reporting the user-requested deletion as if it never happened.
                    LogUtil.e(TAG, "Failed to clean profiles for deleted subscription $subid", e)
                }
            }
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to remove subscription $subid", e)
            false
        }
    }

    /**
     * Encodes the subscription.
     *
     * @param guid The subscription GUID.
     * @param subItem The subscription item.
     */
    fun encodeSubscription(guid: String, subItem: SubscriptionItem): String? {
        val key = guid.ifBlank { Utils.getUuid() }
        return try {
            withProfileIndexLock {
                val subsList = decodeSubsListForWrite()
                val needsIndexWrite = !subsList.contains(key)
                if (needsIndexWrite) subsList.add(key)

                runStorageMutationTransaction {
                    writeSubscriptionPayload(key, subItem)
                    if (needsIndexWrite) writeSubscriptionIndex(subsList)
                }
            }
            key
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to persist subscription $key", e)
            null
        }
    }

    /**
     * Replaces an existing subscription only if it has not changed since the caller read it.
     */
    fun updateSubscription(
        guid: String,
        expected: SubscriptionItem,
        replacement: SubscriptionItem,
    ): Boolean {
        return try {
            withProfileIndexLock {
                val subscriptionIds = decodeSubsListForWrite()
                val current = decodeSubscription(guid)
                if (!SubscriptionUpdateGuard.canCommit(
                        isIndexed = guid in subscriptionIds,
                        current = current,
                        expected = expected,
                    )
                ) {
                    throw SubscriptionUpdateAbortedException()
                }

                runStorageMutationTransaction {
                    writeSubscriptionPayload(guid, replacement)
                }
            }
            true
        } catch (e: SubscriptionUpdateAbortedException) {
            LogUtil.i(TAG, "Skipped stale subscription update for $guid")
            false
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to update subscription $guid", e)
            false
        }
    }

    /**
     * Decodes the subscription.
     *
     * @param subscriptionId The subscription ID.
     * @return The subscription item.
     */
    fun decodeSubscription(subscriptionId: String): SubscriptionItem? {
        val json = subStorage.decodeString(subscriptionId) ?: return null
        return JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java)
    }

    /**
     * Encodes the subscription list.
     *
     * @param subsList The list of subscription IDs.
     */
    fun encodeSubsList(subsList: MutableList<String>): Boolean {
        return try {
            withProfileIndexLock {
                runStorageMutationTransaction {
                    writeSubscriptionIndex(subsList)
                }
            }
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to persist subscription order", e)
            false
        }
    }

    /**
     * Decodes the subscription list.
     *
     * @return The list of subscription IDs.
     */
    fun decodeSubsList(): MutableList<String> {
        val json = mainStorage.decodeString(KEY_SUB_IDS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
        }
    }

    //endregion

    //region Asset

    /**
     * Decodes the asset URLs.
     *
     * @return The list of asset URLs.
     */
    fun decodeAssetUrls(): List<AssetUrlCache> {
        val assetUrlItems = mutableListOf<AssetUrlCache>()
        assetStorage.allKeys()?.forEach { key ->
            val json = assetStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java) ?: AssetUrlItem()
                assetUrlItems.add(AssetUrlCache(key, item))
            }
        }
        return assetUrlItems.sortedBy { it.assetUrl.addedTime }
    }

    /**
     * Removes the asset URL.
     *
     * @param assetid The asset ID.
     */
    fun removeAssetUrl(assetid: String) {
        assetStorage.remove(assetid)
    }

    /**
     * Encodes the asset.
     *
     * @param assetid The asset ID.
     * @param assetItem The asset item.
     */
    fun encodeAsset(assetid: String, assetItem: AssetUrlItem) {
        val key = assetid.ifBlank { Utils.getUuid() }
        assetStorage.encode(key, JsonUtil.toJson(assetItem))
    }

    /**
     * Decodes the asset.
     *
     * @param assetid The asset ID.
     * @return The asset item.
     */
    fun decodeAsset(assetid: String): AssetUrlItem? {
        val json = assetStorage.decodeString(assetid) ?: return null
        return JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java)
    }

    //endregion

    //region Routing

    /**
     * Decodes the routing rulesets.
     *
     * @return The list of routing rulesets.
     */
    fun decodeRoutingRulesets(): MutableList<RulesetItem>? {
        val ruleset = settingsStorage.decodeString(PREF_ROUTING_RULESET)
        if (ruleset.isNullOrEmpty()) return null
        return JsonUtil.fromJsonSafe(ruleset, Array<RulesetItem>::class.java)?.toMutableList() ?: mutableListOf()
    }

    /**
     * Encodes the routing rulesets.
     *
     * @param rulesetList The list of routing rulesets.
     */
    fun encodeRoutingRulesets(rulesetList: MutableList<RulesetItem>?) {
        if (rulesetList.isNullOrEmpty())
            encodeSettings(PREF_ROUTING_RULESET, "")
        else
            encodeSettings(PREF_ROUTING_RULESET, JsonUtil.toJson(rulesetList))
    }

    //endregion

    //region settings
    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: String?): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Int): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Long): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Float): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Boolean): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: MutableSet<String>): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Decodes the settings string.
     *
     * @param key The settings key.
     * @return The settings value.
     */
    fun decodeSettingsString(key: String): String? {
        return settingsStorage.decodeString(key)
    }

    /**
     * Decodes the settings string.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsString(key: String, defaultValue: String?): String? {
        return settingsStorage.decodeString(key, defaultValue)
    }

    /**
     * Decodes the settings integer.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsInt(key: String, defaultValue: Int): Int {
        return settingsStorage.decodeInt(key, defaultValue)
    }

    /**
     * Decodes the settings long.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsLong(key: String, defaultValue: Long): Long {
        return settingsStorage.decodeLong(key, defaultValue)
    }

    /**
     * Decodes the settings float.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsFloat(key: String, defaultValue: Float): Float {
        return settingsStorage.decodeFloat(key, defaultValue)
    }

    /**
     * Decodes the settings boolean.
     *
     * @param key The settings key.
     * @return The settings value.
     */
    fun decodeSettingsBool(key: String): Boolean {
        return settingsStorage.decodeBool(key, false)
    }

    /**
     * Decodes the settings boolean.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsBool(key: String, defaultValue: Boolean): Boolean {
        return settingsStorage.decodeBool(key, defaultValue)
    }

    /**
     * Decodes the settings string set.
     *
     * @param key The settings key.
     * @return The settings value.
     */
    fun decodeSettingsStringSet(key: String): MutableSet<String>? {
        return settingsStorage.decodeStringSet(key)
    }


    /**
     * Encodes the start on boot setting.
     *
     * @param startOnBoot Whether to start on boot.
     */
    fun encodeStartOnBoot(startOnBoot: Boolean) {
        encodeSettings(PREF_IS_BOOTED, startOnBoot)
    }

    /**
     * Decodes the start on boot setting.
     *
     * @return Whether to start on boot.
     */
    fun decodeStartOnBoot(): Boolean {
        return decodeSettingsBool(PREF_IS_BOOTED, false)
    }

    //endregion

    //region WebDAV

    /**
     * Encodes the WebDAV config as JSON into storage.
     */
    fun encodeWebDavConfig(config: WebDavConfig): Boolean {
        return mainStorage.encode(KEY_WEBDAV_CONFIG, JsonUtil.toJson(config))
    }

    /**
     * Decodes the WebDAV config from storage.
     */
    fun decodeWebDavConfig(): WebDavConfig? {
        val json = mainStorage.decodeString(KEY_WEBDAV_CONFIG) ?: return null
        return JsonUtil.fromJsonSafe(json, WebDavConfig::class.java)
    }

    //endregion

    //region Compose helpers for Settings

    /**
     * MMKV-backed String state, auto-persists and notifies on change.
     */
    @Composable
    fun rememberMmkvString(
        key: String,
        default: String = ""
    ): MutableState<String> {
        val state = remember(key) {
            mutableStateOf(decodeSettingsString(key, default) ?: default)
        }

        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }

    /**
     * MMKV-backed Boolean state, auto-persists and notifies on change.
     */
    @Composable
    fun rememberMmkvBool(
        key: String,
        default: Boolean = false
    ): MutableState<Boolean> {
        val state = remember(key) {
            mutableStateOf(decodeSettingsBool(key, default))
        }

        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }

    //endregion
}

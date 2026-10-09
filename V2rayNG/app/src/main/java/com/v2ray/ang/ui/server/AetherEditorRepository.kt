package com.v2ray.ang.ui.server

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.core.AetherIdentityManager
import com.v2ray.ang.core.AetherIdentityStatus
import com.v2ray.ang.core.AetherScanResult
import com.v2ray.ang.core.AetherScanner
import com.v2ray.ang.core.PsiphonServerList
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherKeyKind
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The daemon's live Aether session; [protocol] is null when only its listener could be seen. */
data class AetherSession(val protocol: AetherProtocol?) {

    /** A scan opens a second tunnel on the scanned protocol's key, which disturbs a session using that key. */
    fun disturbedByScanOf(protocol: AetherProtocol): Boolean =
        this.protocol == null || AetherIdentityManager.sharesIdentity(protocol, this.protocol)

    /**
     * Whether the session uses a key that new keys of [kind] would replace; one whose protocol is unknown
     * may use any. The keys of the other protocols can change under it.
     */
    fun usesKeysOf(kind: AetherKeyKind): Boolean =
        protocol == null || AetherIdentityManager.filesOf(protocol).any { it in AetherIdentityManager.filesOf(kind) }
}

interface AetherEditorSource {
    suspend fun isCoreAvailable(): Boolean

    /** Whether the Psiphon client is shipped with this build; a profile with Psiphon cannot connect without it. */
    suspend fun isPsiphonAvailable(): Boolean

    /** Whether the pluggable transport is shipped with this build; without it Tor has no bridges where it is blocked. */
    suspend fun isTorTransportsAvailable(): Boolean

    /** The daemon's live Aether session, scanning or connected, or null; every Aether profile shares its key files. */
    suspend fun activeSession(): AetherSession?
    suspend fun scan(profile: ProfileItem, onOutput: (String) -> Unit): AetherScanResult?
    suspend fun identityStatus(protocol: AetherProtocol): AetherIdentityStatus

    /** Which of [files], key files of the identity folder, are not there or not readable as keys. */
    suspend fun missingKeys(files: List<String>): List<String>

    /** Forgets what the Psiphon client has learned, so that its next start begins again; true when it is gone. */
    suspend fun clearPsiphonData(): Boolean

    /** The exit countries on offer, ISO codes sorted: those of the app's Psiphon server list and those Psiphon last reported. */
    suspend fun psiphonRegions(): List<String>

    /** The Aether listen port of the settings, which every core of a profile listens on. */
    suspend fun listenPort(): Int
}

class AetherEditorRepository(private val context: Context) : AetherEditorSource {

    override suspend fun isCoreAvailable(): Boolean =
        withContext(Dispatchers.IO) { AetherCoreManager.isSupported(context) }

    override suspend fun isPsiphonAvailable(): Boolean =
        withContext(Dispatchers.IO) { AetherCoreManager.isPsiphonSupported(context) }

    override suspend fun isTorTransportsAvailable(): Boolean =
        withContext(Dispatchers.IO) { AetherCoreManager.isTorTransportsSupported(context) }

    // The daemon is the only authority on its state, so this looks for its core process and its
    // listener instead of a UI-side flag; see [sessionOf].
    override suspend fun activeSession(): AetherSession? = withContext(Dispatchers.IO) {
        sessionOf(AetherCoreManager.sessionProtocol(context), AetherCoreManager.canListProcesses()) {
            AetherCoreManager.answersSocks(AetherCoreManager.socksPort)
        }
    }

    override suspend fun scan(profile: ProfileItem, onOutput: (String) -> Unit): AetherScanResult? =
        AetherScanner.scan(context, profile, onOutput)

    override suspend fun identityStatus(protocol: AetherProtocol): AetherIdentityStatus =
        AetherIdentityManager.status(context, protocol)

    override suspend fun missingKeys(files: List<String>): List<String> = AetherIdentityManager.missing(context, files)

    override suspend fun clearPsiphonData(): Boolean = withContext(Dispatchers.IO) {
        PsiphonServerList.forgetRemembered()
        AetherCoreManager.clearPsiphonState(context.filesDir, AetherIdentityManager.workDir(context))
    }

    companion object {
        /**
         * The live session told by the [protocol] its core process names, which covers the scanning
         * phase, before the listener exists. Only where the processes cannot be listed does a listener
         * on the Aether port, which [listenerAnswers] tells, stand in for it, and then the protocol stays
         * unknown: elsewhere that listener is the core of a latency test, which listens on the same port.
         */
        internal fun sessionOf(protocol: AetherProtocol?, processesListed: Boolean, listenerAnswers: () -> Boolean): AetherSession? =
            protocol?.let(::AetherSession) ?: AetherSession(protocol = null).takeIf { !processesListed && listenerAnswers() }
    }

    override suspend fun listenPort(): Int = withContext(Dispatchers.IO) { AetherCoreManager.socksPort }

    override suspend fun psiphonRegions(): List<String> = withContext(Dispatchers.IO) {
        val entries = PsiphonServerList.entriesFile(File(Utils.userAssetPath(context)), AetherIdentityManager.workDir(context)) { problem ->
            LogUtil.w(AppConfig.TAG, "AetherEditor: ${AppConfig.PSIPHON_SERVERS_DAT} is not a usable Psiphon list", problem)
        }
        val listed = entries?.let { file -> runCatching { PsiphonServerList.regions(file.readText()) }.getOrDefault(emptySet()) }.orEmpty()
        (listed + PsiphonServerList.remembered()).toSortedSet().toList()
    }
}

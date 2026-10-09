package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.AetherKeyKind
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

data class AetherIdentity(
    val deviceId: String,
    val ipv4: String,
    val ipv6: String,
)

data class AetherIdentityStatus(
    val protocol: AetherProtocol,
    val primary: AetherIdentity?,
    val secondary: AetherIdentity? = null,
)

/** A key file of the identity folder, one of [AetherIdentityManager.KEY_FILES], with the identity it holds; null without one. */
data class AetherKey(
    val file: String,
    val identity: AetherIdentity?,
)

object AetherIdentityManager {

    const val BASE_FILE = "aether.toml"
    const val MASQUE_FILE = "aether-masque.toml"
    const val MASQUE_INNER_FILE = "aether-masque-secondary.toml"
    const val WIREGUARD_FILE = "aether-wg.toml"
    const val WIREGUARD_INNER_FILE = "aether-wg-secondary.toml"

    /** The key files of every kind, in the order the core registers them. */
    internal val KEY_FILES = listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE, MASQUE_FILE, MASQUE_INNER_FILE)

    private const val WORK_DIR = "aether"

    /**
     * Where a renewal gathers its new keys. The keys in use stay in [WORK_DIR], untouched, until
     * every new key it registers is here; only then does each new key take the place of the old one.
     */
    private const val RENEWAL_DIR = "aether-renewal"

    /**
     * Written into [RENEWAL_DIR] once every new key of a renewal is there. From then on the new keys
     * are the keys: whatever stops the moving of them, [settle] finishes it.
     */
    internal const val READY_MARK = "ready"

    /**
     * Where the renewal before [RENEWAL_DIR] moved the keys in use while it registered new ones.
     * An app killed during such a renewal left it behind, holding keys nothing uses any more;
     * [settleIn] removes it the first time the app uses Aether.
     */
    private const val PREVIOUS_DIR = "aether-previous"

    /** Four registrations and two MASQUE key enrollments at most, each of which the core may retry. */
    private const val RENEW_TIMEOUT_MS = 4 * 60_000L

    /**
     * A renewal through Tor or Psiphon, which a command written by hand may ask for, waits for the
     * carrier to come up first, which takes what a scan may take.
     */
    private const val RENEW_THROUGH_CARRIER_TIMEOUT_MS = 10 * 60_000L

    private const val SOURCE = "aether-key"

    private val identityField = Regex("""^(device_id|ipv4|ipv6)\s*=\s*"([^"]*)"$""")
    private val multilineDelimiter = Regex("\"\"\"|'''")

    /** The core's last word in a registration, once every key it was asked for is saved. */
    private val keysRegistered = Regex("""identities ready: \S""")

    /** One renewal at a time: they share [RENEWAL_DIR]. */
    private val renewal = Mutex()

    fun workDir(context: Context): File = File(context.filesDir, WORK_DIR)

    private fun renewalDir(context: Context): File = File(context.filesDir, RENEWAL_DIR)

    suspend fun status(context: Context, protocol: AetherProtocol): AetherIdentityStatus =
        withContext(Dispatchers.IO) {
            settle(context)
            status(workDir(context), protocol)
        }

    /** Every key of the identity folder, each of [KEY_FILES] with the identity it holds. */
    suspend fun keys(context: Context): List<AetherKey> =
        withContext(Dispatchers.IO) {
            settle(context)
            keys(workDir(context), KEY_FILES)
        }

    /**
     * Registers new keys of [kind] by running the core on [arguments], which register them, see
     * [AetherKeys], and puts them in place of the keys in use once all of them are there. Until then
     * the keys in use stay where they are, untouched, so a failure, a cancellation or the app being
     * killed leaves them in use; the keys of another kind are never touched. The core dials out
     * through the exit-node [exit]. Returns the new keys, or null when the keys in use stay.
     */
    suspend fun renew(
        context: Context,
        kind: AetherKeyKind,
        arguments: List<String>,
        exit: AetherExit,
        onOutput: (String) -> Unit,
    ): List<AetherKey>? = renewal.withLock {
        val workDir = workDir(context)
        val renewalDir = renewalDir(context)
        val files = filesOf(kind)
        if (!renew(workDir, renewalDir, files) { register(context, arguments, exit, renewalDir, onOutput) }) return@withLock null
        withContext(Dispatchers.IO) {
            if (!settle(workDir, renewalDir)) {
                LogUtil.w(AppConfig.TAG, "AetherIdentity: the new keys are ready but not all in place yet; the next core start moves the rest")
            }
            keys(workDir, files)
        }
    }

    /** [settleIn] the app's files folder; whatever reads the keys calls it first. */
    fun settle(context: Context): Boolean = settleIn(context.filesDir)

    /**
     * Removes [PREVIOUS_DIR] from [filesDir], the app's files folder, with the keys an interrupted
     * renewal of an older version left there, and then does what [settle] does on the identity
     * folder and the renewal folder in it; the keys in use stay as they are. A folder that cannot
     * all go now is tried again at the next use.
     */
    internal fun settleIn(filesDir: File): Boolean {
        if (!File(filesDir, PREVIOUS_DIR).deleteRecursively()) {
            LogUtil.w(AppConfig.TAG, "AetherIdentity: the keys an interrupted renewal of an older version left could not all be removed; the next use tries again")
        }
        return settle(File(filesDir, WORK_DIR), File(filesDir, RENEWAL_DIR))
    }

    /**
     * Runs the core once on [arguments] to register the keys they ask for into [renewalDir]: it
     * registers them, through a carrier if the arguments name one, and ends without scanning or
     * opening a tunnel. The run is over at its word that the keys are saved, which it writes at the
     * info level.
     */
    private suspend fun register(
        context: Context,
        arguments: List<String>,
        exit: AetherExit,
        renewalDir: File,
        onOutput: (String) -> Unit,
    ): Boolean = AetherCoreManager.runUntil(
        context = context,
        arguments = AetherCoreManager.withInfoLines(arguments),
        exit = exit,
        timeoutMs = if (AetherCoreManager.reachesWarpThroughCarrier(arguments)) RENEW_THROUGH_CARRIER_TIMEOUT_MS else RENEW_TIMEOUT_MS,
        source = SOURCE,
        onOutput = onOutput,
        keysDir = renewalDir,
    ) { line -> line.takeIf(::isRegistered) } != null

    internal fun status(workDir: File, protocol: AetherProtocol): AetherIdentityStatus = when (protocol) {
        AetherProtocol.MASQUE -> AetherIdentityStatus(protocol, read(File(workDir, MASQUE_FILE)))
        AetherProtocol.WIREGUARD -> AetherIdentityStatus(protocol, read(File(workDir, WIREGUARD_FILE)))
        AetherProtocol.GOOL -> AetherIdentityStatus(
            protocol,
            read(File(workDir, WIREGUARD_FILE)),
            read(File(workDir, WIREGUARD_INNER_FILE)),
        )

        AetherProtocol.MIM -> AetherIdentityStatus(
            protocol,
            read(File(workDir, MASQUE_FILE)),
            read(File(workDir, MASQUE_INNER_FILE)),
        )
    }

    /** The key files a registration of [kind] writes, in the order of [KEY_FILES]. */
    fun filesOf(kind: AetherKeyKind): List<String> = when (kind) {
        AetherKeyKind.ALL -> KEY_FILES
        AetherKeyKind.WIREGUARD -> listOf(WIREGUARD_FILE)
        AetherKeyKind.MASQUE -> listOf(MASQUE_FILE)
        AetherKeyKind.GOOL -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        AetherKeyKind.MIM -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
    }

    /** The key files a tunnel of [protocol] uses: its own, and the inner hop's for a two-hop one. */
    fun filesOf(protocol: AetherProtocol): List<String> = when (protocol) {
        AetherProtocol.MASQUE -> listOf(MASQUE_FILE)
        AetherProtocol.WIREGUARD -> listOf(WIREGUARD_FILE)
        AetherProtocol.GOOL -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        AetherProtocol.MIM -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
    }

    /**
     * The key files a core on [arguments] needs: those of the protocol they run, read as the core reads it, and none
     * when they run Psiphon or Tor alone, with no WARP tunnel.
     */
    fun filesNeededBy(arguments: List<String>): List<String> =
        if (AetherCoreManager.psiphonModeOf(arguments) == AetherPsiphon.ONLY || AetherCoreManager.torModeOf(arguments) == AetherTor.ONLY) {
            emptyList()
        } else {
            filesOf(AetherCoreManager.protocolOf(arguments))
        }

    /** Which of [files] the identity folder lacks: not there, or not readable as a key. */
    suspend fun missing(context: Context, files: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            settle(context)
            missing(workDir(context), files)
        }

    internal fun missing(dir: File, files: List<String>): List<String> = files.filter { read(File(dir, it)) == null }

    /** Each of [files] in [dir] with the identity it holds. */
    internal fun keys(dir: File, files: List<String>): List<AetherKey> = files.map { AetherKey(it, read(File(dir, it))) }

    internal fun parse(text: String): AetherIdentity? {
        val fields = mutableMapOf<String, String>()
        var insideMultiline = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!insideMultiline) {
                identityField.matchEntire(line)?.destructured?.let { (key, value) -> fields.putIfAbsent(key, value) }
            }
            if (multilineDelimiter.findAll(line).count() % 2 == 1) {
                insideMultiline = !insideMultiline
            }
        }
        val deviceId = fields["device_id"]?.takeIf { it.isNotBlank() } ?: return null
        return AetherIdentity(deviceId, fields["ipv4"].orEmpty(), fields["ipv6"].orEmpty())
    }

    /** The tunnels over MASQUE share the MASQUE key and the others the WireGuard key; a two-hop tunnel adds a second key of its kind. */
    fun sharesIdentity(first: AetherProtocol, second: AetherProtocol): Boolean =
        first.overMasque == second.overMasque

    internal fun isRegistered(line: String): Boolean = keysRegistered.containsMatchIn(line)

    /**
     * Gathers new keys in [renewalDir] through [register] and moves them over the keys in [workDir]
     * once every one of [files] is there and reads as a key. Nothing in [workDir] is touched before
     * that moment, so whatever stops a renewal earlier leaves the keys in use as they were; a stop
     * after it is finished by [settle]. A key not among [files] stays as it is. True when the new
     * keys took the place of the old ones.
     */
    internal suspend fun renew(
        workDir: File,
        renewalDir: File,
        files: List<String>,
        register: suspend () -> Boolean,
    ): Boolean {
        val cleared = withContext(Dispatchers.IO) {
            // A renewal whose keys were all ready is finished first; what one stopped earlier left is no key anyone uses.
            settle(workDir, renewalDir) && renewalDir.deleteRecursively() && renewalDir.mkdirs()
        }
        if (!cleared) return false
        // A cancellation can be delivered as the result of a blocking step comes back, after the step
        // itself has run; whether the new keys were marked ready is therefore recorded inside that
        // step, and the finally block goes by the record rather than by a value the step returned.
        val ready = AtomicBoolean(false)
        try {
            if (register()) {
                withContext(Dispatchers.IO) {
                    // A key the run left beside the ones asked for goes with the folder rather than into place.
                    (KEY_FILES - files.toSet()).forEach { File(renewalDir, it).delete() }
                    ready.set(isComplete(renewalDir, files) && markReady(renewalDir))
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (ready.get()) settle(workDir, renewalDir) else renewalDir.deleteRecursively()
            }
        }
        return ready.get()
    }

    /**
     * Moves the new keys of a renewal whose keys were all ready over the keys in [workDir], one
     * after another, and then removes [renewalDir]. Without the ready mark there is nothing to move:
     * the new keys are incomplete, or a renewal is still gathering them, and the keys in use stay.
     * Another process may be finishing the same renewal at the same time; a key it moved first is
     * simply gone here. True when no renewal with ready keys waits any longer.
     */
    internal fun settle(workDir: File, renewalDir: File): Boolean {
        if (!File(renewalDir, READY_MARK).isFile) return true
        workDir.mkdirs()
        for (name in KEY_FILES) {
            val renewed = File(renewalDir, name)
            if (renewed.isFile && !moveOver(renewed, File(workDir, name))) return false
        }
        // The mark goes with the folder, after every key: until then it tells that keys are left to move.
        return renewalDir.deleteRecursively()
    }

    /**
     * Moves [source] over [target] in one step that replaces the key in use, or leaves it as it
     * was; true once [source] is gone, moved by this call or by another process finishing the same
     * renewal.
     */
    private fun moveOver(source: File, target: File): Boolean = try {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true
    } catch (_: IOException) {
        !source.isFile
    }

    /** Whether every one of [files] is in [dir] and reads as a key. */
    internal fun isComplete(dir: File, files: List<String>): Boolean = files.all { read(File(dir, it)) != null }

    private fun markReady(dir: File): Boolean = try {
        File(dir, READY_MARK).createNewFile()
    } catch (_: IOException) {
        false
    }

    private fun read(file: File): AetherIdentity? = try {
        if (file.isFile) parse(file.readText()) else null
    } catch (_: IOException) {
        null
    }
}

package com.v2ray.ang.ui.server

import android.app.Application
import android.util.Log
import com.v2ray.ang.R
import com.v2ray.ang.core.AetherExit
import com.v2ray.ang.core.AetherIdentity
import com.v2ray.ang.core.AetherIdentityManager
import com.v2ray.ang.core.AetherKey
import com.v2ray.ang.core.AetherKeys
import com.v2ray.ang.core.AetherKeysSettings
import com.v2ray.ang.enums.AetherFingerprint
import com.v2ray.ang.enums.AetherKeyKind
import com.v2ray.ang.enums.AetherProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class AetherKeysViewModelTest {

    private val oldKey = AetherIdentity("a1b2c3d4e5f6", "172.16.0.2", "2606:4700:110:8a36::1")
    private val newKey = AetherIdentity("f6e5d4c3b2a1", "172.16.0.2", "2606:4700:110:8a36::2")

    /** A run of the core as the source was asked for it. */
    private data class Run(val kind: AetherKeyKind, val arguments: List<String>, val exit: AetherExit)

    private class FakeSource : AetherKeysSource {
        var available = true
        var session: AetherSession? = null
        var stored = AetherKeysSettings()
        val saved = mutableListOf<AetherKeysSettings>()
        var inUse: List<AetherKey> = AetherIdentityManager.KEY_FILES.map { AetherKey(it, null) }
        val runs = mutableListOf<Run>()
        var renewer: suspend ((String) -> Unit) -> List<AetherKey>? = { null }

        override suspend fun isCoreAvailable() = available
        override suspend fun activeSession() = session
        override suspend fun loadSettings() = stored
        override suspend fun saveSettings(settings: AetherKeysSettings) {
            saved += settings
        }

        override suspend fun keys() = inUse
        override suspend fun renew(kind: AetherKeyKind, arguments: List<String>, exit: AetherExit, onOutput: (String) -> Unit): List<AetherKey>? {
            runs += Run(kind, arguments, exit)
            return renewer(onOutput)
        }
    }

    private val source = FakeSource()

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AetherKeysViewModel(mock<Application>(), source)

    private fun AetherKeysViewModel.texts() = log.value.map { it.text }

    private fun resource(id: Int, vararg args: String) = AetherLogText.Resource(id, args.toList())

    private fun missing(file: String) = AetherKeysViewModel.keyLine(AetherKey(file, null))

    @Test
    fun opensOnTheSavedSettingsAndShowsEveryKeyInUse() {
        source.available = false
        source.stored = AetherKeysSettings(kind = AetherKeyKind.GOOL, fingerprint = AetherFingerprint.FIREFOX, ech = true)
        source.inUse = listOf(
            AetherKey(AetherIdentityManager.WIREGUARD_FILE, oldKey),
            AetherKey(AetherIdentityManager.WIREGUARD_INNER_FILE, null),
            AetherKey(AetherIdentityManager.MASQUE_FILE, oldKey),
            AetherKey(AetherIdentityManager.MASQUE_INNER_FILE, null),
        )
        val viewModel = viewModel()

        assertEquals(source.stored, viewModel.settings)
        assertFalse(viewModel.isCoreAvailable.value)
        assertFalse(viewModel.isRenewing.value)
        assertNull(viewModel.session.value)
        assertNull(viewModel.notice.value)
        assertTrue(source.saved.isEmpty())
        assertEquals(
            listOf(
                resource(R.string.aether_log_wireguard_key_ready, "a1b2c3d4…", "172.16.0.2", "2606:4700:110:8a36::1"),
                resource(R.string.aether_log_wireguard_inner_key_missing),
                resource(R.string.aether_log_masque_key_ready, "a1b2c3d4…", "172.16.0.2", "2606:4700:110:8a36::1"),
                resource(R.string.aether_log_masque_inner_key_missing),
            ),
            viewModel.texts()
        )
    }

    @Test
    fun eachChangeIsKeptAndSaved() {
        val viewModel = viewModel()

        viewModel.setKind(AetherKeyKind.MIM)
        viewModel.setEnrollAddress("188.114.97.6:443")
        viewModel.setEch(true)
        viewModel.setEchDns("https://1.1.1.1/dns-query")
        viewModel.setEchDomain("example.com")
        viewModel.setFingerprint(AetherFingerprint.SEMI_PYTHON)
        viewModel.setFinalMask("""{"tcp": []}""")
        viewModel.setDialMode("ForceIP")

        val expected = AetherKeysSettings(
            kind = AetherKeyKind.MIM,
            enrollAddress = "188.114.97.6:443",
            ech = true,
            echDns = "https://1.1.1.1/dns-query",
            echDomain = "example.com",
            fingerprint = AetherFingerprint.SEMI_PYTHON,
            finalMask = """{"tcp": []}""",
            dialMode = "ForceIP",
        )
        assertEquals(expected, viewModel.settings)
        assertEquals(8, source.saved.size)
        assertEquals(expected, source.saved.last())

        // A change to what is already there saves nothing.
        viewModel.setKind(AetherKeyKind.MIM)
        assertEquals(8, source.saved.size)
    }

    @Test
    fun aCommandWrittenBackToTheBuiltOneFollowsTheSettingsAgain() {
        val viewModel = viewModel()
        val built = AetherKeys.builtCommand(viewModel.settings!!)

        viewModel.setCommand("$built --tor-reverse")
        assertEquals("$built --tor-reverse", viewModel.settings?.command)
        assertTrue(AetherKeys.isCustom(viewModel.settings!!))

        viewModel.setCommand(built)
        assertEquals("", viewModel.settings?.command)

        viewModel.setCommand("aether --register wg")
        viewModel.useSettings()
        assertEquals("", viewModel.settings?.command)
        assertEquals(AetherKeysSettings(), source.saved.last())
    }

    @Test
    fun gettingKeysRunsTheSettingsAndShowsTheNewKeysOnceTheyAreInPlace() {
        val pending = CompletableDeferred<List<AetherKey>?>()
        source.stored = AetherKeysSettings(kind = AetherKeyKind.GOOL, fingerprint = AetherFingerprint.FIREFOX, finalMask = """{"tcp": []}""", dialMode = "ForceIP")
        source.renewer = { onOutput ->
            onOutput("[2026-10-03T10:00:00.000Z INFO  aether] [+] identities ready: wireguard, wireguard inner")
            pending.await()
        }
        val viewModel = viewModel()
        val opened = viewModel.texts()

        viewModel.getKeys()
        assertTrue(viewModel.isRenewing.value)
        assertEquals(
            listOf(Run(AetherKeyKind.GOOL, AetherKeys.arguments(source.stored), AetherExit("""{"tcp": []}""", "ForceIP"))),
            source.runs
        )
        // The settings stay as the run took them while it goes on.
        viewModel.setFingerprint(AetherFingerprint.CHROME)
        assertEquals(AetherFingerprint.FIREFOX, viewModel.settings?.fingerprint)

        pending.complete(
            listOf(
                AetherKey(AetherIdentityManager.WIREGUARD_FILE, newKey),
                AetherKey(AetherIdentityManager.WIREGUARD_INNER_FILE, newKey),
            )
        )

        assertFalse(viewModel.isRenewing.value)
        assertEquals(AetherKeysNotice.Renewed, viewModel.notice.value)
        assertEquals(
            opened + listOf(
                resource(R.string.aether_log_key_renewing),
                AetherLogText.Raw("[+] identities ready: wireguard, wireguard inner"),
                resource(R.string.aether_log_key_renewed),
                resource(R.string.aether_log_wireguard_key_ready, "f6e5d4c3…", "172.16.0.2", "2606:4700:110:8a36::2"),
                resource(R.string.aether_log_wireguard_inner_key_ready, "f6e5d4c3…", "172.16.0.2", "2606:4700:110:8a36::2"),
            ),
            viewModel.texts()
        )

        // The page stays, for keys of another kind.
        viewModel.onNoticeShown()
        assertNull(viewModel.notice.value)
        viewModel.setKind(AetherKeyKind.MASQUE)
        source.renewer = { null }
        viewModel.getKeys()
        assertEquals(AetherKeyKind.MASQUE, source.runs.last().kind)
    }

    @Test
    fun aCommandWrittenByHandRunsAsWrittenWithTheKeysItNames() {
        source.stored = AetherKeysSettings(enrollAddress = "-x", command = "aether --register mim --psiphon-reverse")
        val viewModel = viewModel()

        viewModel.getKeys()

        assertEquals(listOf(Run(AetherKeyKind.MIM, listOf("--register", "mim", "--psiphon-reverse"), AetherExit())), source.runs)
    }

    @Test
    fun settingsThatCannotRunAreToldAndNothingRuns() {
        val viewModel = viewModel()
        val opened = viewModel.texts()

        viewModel.setEnrollAddress("-x")
        viewModel.getKeys()
        assertEquals(AetherKeysNotice.Invalid(R.string.aether_keys_invalid_enroll_address), viewModel.notice.value)
        viewModel.onNoticeShown()

        viewModel.setEnrollAddress("")
        viewModel.setCommand("aether --protocol wg")
        viewModel.getKeys()
        assertEquals(AetherKeysNotice.Invalid(R.string.aether_keys_invalid_command), viewModel.notice.value)

        assertTrue(source.runs.isEmpty())
        assertFalse(viewModel.isRenewing.value)
        assertEquals(opened, viewModel.texts())
    }

    @Test
    fun eachProblemHasItsMessage() {
        val messages = AetherKeys.Problem.entries.associateWith { AetherKeysViewModel.messageOf(it) }
        assertEquals(R.string.aether_keys_invalid_enroll_address, messages[AetherKeys.Problem.INVALID_ENROLL_ADDRESS])
        assertEquals(R.string.aether_invalid_ech_dns, messages[AetherKeys.Problem.INVALID_ECH_DNS])
        assertEquals(R.string.aether_invalid_ech_domain, messages[AetherKeys.Problem.INVALID_ECH_DOMAIN])
        assertEquals(R.string.aether_lab_exit_final_mask, messages[AetherKeys.Problem.INVALID_FINAL_MASK])
        assertEquals(R.string.aether_keys_invalid_command, messages[AetherKeys.Problem.INVALID_COMMAND])
    }

    @Test
    fun aSessionKeepsTheKeysItUsesButNotTheOthers() {
        val viewModel = viewModel()
        val opened = viewModel.texts()

        // Every key would be replaced, the session's among them.
        source.session = AetherSession(AetherProtocol.MASQUE)
        viewModel.getKeys()
        assertTrue(source.runs.isEmpty())
        assertEquals(AetherSession(AetherProtocol.MASQUE), viewModel.session.value)
        assertFalse(viewModel.isRenewing.value)
        val blocked = viewModel.log.value.last()
        assertEquals(resource(R.string.aether_renew_blocked), blocked.text)
        assertEquals(Log.WARN, blocked.priority)
        assertEquals(opened.size + 1, viewModel.texts().size)

        // The WireGuard keys are not the session's.
        viewModel.setKind(AetherKeyKind.GOOL)
        viewModel.getKeys()
        assertEquals(AetherKeyKind.GOOL, source.runs.single().kind)

        source.session = null
        viewModel.refreshSession()
        assertNull(viewModel.session.value)
    }

    @Test
    fun aFailedRunSaysTheKeysInUseWereKept() {
        val viewModel = viewModel()

        viewModel.getKeys()

        assertFalse(viewModel.isRenewing.value)
        assertNull(viewModel.notice.value)
        val failure = viewModel.log.value.last()
        assertEquals(resource(R.string.aether_log_key_renew_failed), failure.text)
        assertEquals(Log.ERROR, failure.priority)
    }

    @Test
    fun cancellingStopsTheRunAndShowsTheKeysThatChanged() {
        var stopped = false
        source.renewer = {
            try {
                // The new keys were in place by the time the run was stopped.
                source.inUse = source.inUse.map { if (it.file == AetherIdentityManager.MASQUE_FILE) AetherKey(it.file, newKey) else it }
                awaitCancellation()
            } finally {
                stopped = true
            }
        }
        val viewModel = viewModel()
        val opened = viewModel.texts()

        viewModel.getKeys()
        viewModel.cancel()

        assertTrue(stopped)
        assertFalse(viewModel.isRenewing.value)
        assertNull(viewModel.notice.value)
        val cancelled = viewModel.log.value.first { it.text == resource(R.string.aether_log_key_renew_cancelled) }
        assertEquals(Log.WARN, cancelled.priority)
        assertEquals(
            opened + listOf(
                resource(R.string.aether_log_key_renewing),
                resource(R.string.aether_log_key_renew_cancelled),
                resource(R.string.aether_log_masque_key_ready, "f6e5d4c3…", "172.16.0.2", "2606:4700:110:8a36::2"),
            ),
            viewModel.texts()
        )
        assertEquals(missing(AetherIdentityManager.WIREGUARD_FILE), opened.first())
    }

    @Test
    fun aCancelledRunStaysBusyUntilItHasStopped() {
        val stopped = CompletableDeferred<Unit>()
        source.renewer = {
            try {
                awaitCancellation()
            } finally {
                // The core is still being ended, as a real one takes a moment to.
                withContext(NonCancellable) { stopped.await() }
            }
        }
        val viewModel = viewModel()

        viewModel.getKeys()
        viewModel.cancel()

        // Nothing else starts, nothing changes, and a second cancel adds nothing.
        assertTrue(viewModel.isRenewing.value)
        viewModel.getKeys()
        viewModel.setKind(AetherKeyKind.WIREGUARD)
        viewModel.cancel()
        assertEquals(1, source.runs.size)
        assertEquals(AetherKeyKind.ALL, viewModel.settings?.kind)
        assertEquals(1, viewModel.texts().count { it == resource(R.string.aether_log_key_renew_cancelled) })

        stopped.complete(Unit)

        assertFalse(viewModel.isRenewing.value)
        source.renewer = { null }
        viewModel.getKeys()
        assertEquals(2, source.runs.size)
    }

    @Test
    fun cancellingWhenNoRunGoesOnDoesNothing() {
        val viewModel = viewModel()
        val opened = viewModel.texts()
        viewModel.cancel()
        assertEquals(opened, viewModel.texts())

        source.renewer = { listOf(AetherKey(AetherIdentityManager.MASQUE_FILE, newKey)) }
        viewModel.setKind(AetherKeyKind.MASQUE)
        viewModel.getKeys()
        val finished = viewModel.texts()
        viewModel.cancel()

        assertEquals(finished, viewModel.texts())
        assertEquals(resource(R.string.aether_log_masque_key_ready, "f6e5d4c3…", "172.16.0.2", "2606:4700:110:8a36::2"), finished.last())
    }
}

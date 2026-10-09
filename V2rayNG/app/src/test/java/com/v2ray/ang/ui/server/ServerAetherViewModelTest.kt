package com.v2ray.ang.ui.server

import android.app.Application
import android.util.Log
import com.v2ray.ang.R
import com.v2ray.ang.core.AetherIdentity
import com.v2ray.ang.core.AetherIdentityManager
import com.v2ray.ang.core.AetherIdentityStatus
import com.v2ray.ang.core.AetherScanResult
import com.v2ray.ang.dto.AetherEndpoint
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.EConfigType
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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ServerAetherViewModelTest {

    private val profile = ProfileItem.create(EConfigType.AETHER).apply { aetherProtocol = AetherProtocol.MASQUE.type }
    private val found = AetherScanResult(AetherEndpoint("162.159.197.3", 443))
    private val oldKey = AetherIdentity("a1b2c3d4e5f6", "172.16.0.2", "2606:4700:110:8a36::1")

    private class FakeSource : AetherEditorSource {
        var available = true
        var session: AetherSession? = null
        var scanner: suspend (ProfileItem, (String) -> Unit) -> AetherScanResult? = { _, _ -> null }
        var clearer: suspend () -> Boolean = { true }
        var regions: List<String> = emptyList()
        var port = 10819
        val identities = mutableMapOf<AetherProtocol, AetherIdentityStatus>()
        val missingFiles = mutableSetOf<String>()

        override suspend fun isCoreAvailable() = available
        override suspend fun isPsiphonAvailable(): Boolean = false
        override suspend fun isTorTransportsAvailable(): Boolean = false
        override suspend fun activeSession() = session
        override suspend fun scan(profile: ProfileItem, onOutput: (String) -> Unit) = scanner(profile, onOutput)
        override suspend fun identityStatus(protocol: AetherProtocol) =
            identities[protocol] ?: AetherIdentityStatus(protocol, null)

        override suspend fun missingKeys(files: List<String>) = files.filter { it in missingFiles }
        override suspend fun clearPsiphonData() = clearer()
        override suspend fun psiphonRegions() = regions
        override suspend fun listenPort() = port
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

    private fun viewModel() = ServerAetherViewModel(mock<Application>(), source)

    private fun ServerAetherViewModel.texts() = log.value.map { it.text }

    private fun resource(id: Int, vararg args: String) = AetherLogText.Resource(id, args.toList())

    @Test
    fun startsIdleWithAnEmptyLogAndReportsWhetherTheCoreIsAvailable() {
        source.available = false
        val viewModel = viewModel()

        assertEquals(AetherScanState.Idle, viewModel.scanState.value)
        assertFalse(viewModel.isCoreAvailable.value)
        assertNull(viewModel.session.value)
        assertTrue(viewModel.log.value.isEmpty())
    }

    @Test
    fun theCommandIsBuiltOnTheListenPortOfTheSettings() {
        source.port = 20808
        assertEquals(20808, viewModel().listenPort.value)
    }

    @Test
    fun aScanStreamsTheCoreOutputAndEndsWithTheEndpointItFound() {
        val pending = CompletableDeferred<AetherScanResult?>()
        var scanned: ProfileItem? = null
        source.scanner = { profile, onOutput ->
            scanned = profile
            onOutput("[2026-09-11T10:00:00.000Z INFO  aether] [+] candidate ok 162.159.197.3:443 rtt=84ms")
            onOutput("   ")
            pending.await()
        }
        val viewModel = viewModel()

        viewModel.scan(profile)
        assertEquals(AetherScanState.Scanning, viewModel.scanState.value)
        assertSame(profile, scanned)

        pending.complete(found)

        assertEquals(AetherScanState.Found(found), viewModel.scanState.value)
        assertEquals(
            listOf(
                resource(R.string.aether_log_scan_started),
                AetherLogText.Raw("[+] candidate ok 162.159.197.3:443 rtt=84ms"),
                resource(R.string.aether_log_scan_found, "162.159.197.3:443"),
                resource(R.string.aether_log_masque_key_missing),
            ),
            viewModel.texts()
        )

        viewModel.onScanHandled()
        assertEquals(AetherScanState.Idle, viewModel.scanState.value)
    }

    @Test
    fun aScanThatFindsNothingSaysSo() {
        val viewModel = viewModel()

        viewModel.scan(profile)

        assertEquals(AetherScanState.NotFound, viewModel.scanState.value)
        val outcome = viewModel.log.value.first { it.text == resource(R.string.aether_scan_failed) }
        assertEquals(Log.WARN, outcome.priority)
    }

    @Test
    fun bothGoolHopsAreLoggedTogether() {
        val hops = AetherScanResult(AetherEndpoint("162.159.192.1", 2408), AetherEndpoint("188.114.96.1", 894))

        assertEquals(
            resource(R.string.aether_log_scan_found_hops, "162.159.192.1:2408", "188.114.96.1:894"),
            ServerAetherViewModel.scanOutcome(hops)
        )
    }

    @Test
    fun coreOutputKeepsItsLevelButLosesItsHeader() {
        source.scanner = { _, onOutput ->
            onOutput("[2026-09-11T10:00:00.000Z WARN  aether] [-] scan deadline reached")
            onOutput("Error: NoCleanEndpoint")
            null
        }
        val viewModel = viewModel()

        viewModel.scan(profile)

        val output = viewModel.log.value.filter { it.text is AetherLogText.Raw }
        assertEquals(listOf(Log.WARN, Log.ERROR), output.map { it.priority })
        assertEquals(
            listOf(AetherLogText.Raw("[-] scan deadline reached"), AetherLogText.Raw("Error: NoCleanEndpoint")),
            output.map { it.text }
        )
    }

    @Test
    fun aSecondActionWaitsForTheFirstToFinish() {
        val pending = CompletableDeferred<AetherScanResult?>()
        var scans = 0
        var clears = 0
        source.scanner = { _, _ -> scans++; pending.await() }
        source.clearer = { clears++; true }
        val viewModel = viewModel()

        viewModel.scan(profile)
        viewModel.scan(profile)
        viewModel.clearPsiphonData()
        assertEquals(1, scans)
        assertEquals(0, clears)

        pending.complete(null)
        viewModel.onScanHandled()
        viewModel.scan(profile)
        assertEquals(2, scans)
    }

    @Test
    fun cancellingAScanStopsItAndReturnsToIdle() {
        var stopped = false
        source.scanner = { _, _ ->
            try {
                awaitCancellation()
            } finally {
                stopped = true
            }
        }
        val viewModel = viewModel()

        viewModel.scan(profile)
        viewModel.cancelScan()

        assertTrue(stopped)
        assertEquals(AetherScanState.Idle, viewModel.scanState.value)
        assertEquals(resource(R.string.aether_log_scan_cancelled), viewModel.texts().last())
    }

    @Test
    fun cancellingWhenNothingRunsKeepsTheResult() {
        source.scanner = { _, _ -> found }
        val viewModel = viewModel()

        viewModel.scan(profile)
        viewModel.cancelScan()

        assertEquals(AetherScanState.Found(found), viewModel.scanState.value)
    }

    @Test
    fun handlingARunningScanDoesNotHideIt() {
        source.scanner = { _, _ -> awaitCancellation() }
        val viewModel = viewModel()

        viewModel.scan(profile)
        viewModel.onScanHandled()

        assertEquals(AetherScanState.Scanning, viewModel.scanState.value)
    }

    @Test
    fun theKeyStatusIsLoggedOnceUntilItChanges() {
        source.identities[AetherProtocol.MASQUE] = AetherIdentityStatus(AetherProtocol.MASQUE, oldKey)
        val viewModel = viewModel()

        viewModel.showIdentity(AetherProtocol.MASQUE)
        viewModel.showIdentity(AetherProtocol.MASQUE)
        viewModel.showIdentity(AetherProtocol.WIREGUARD)

        assertEquals(
            listOf(
                resource(R.string.aether_log_masque_key_ready, "a1b2c3d4…", "172.16.0.2", "2606:4700:110:8a36::1"),
                resource(R.string.aether_log_wireguard_key_missing),
            ),
            viewModel.texts()
        )
    }

    @Test
    fun aTwoHopProtocolReportsBothHopKeys() {
        for (protocol in listOf(AetherProtocol.GOOL, AetherProtocol.MIM)) {
            val status = AetherIdentityStatus(protocol, oldKey, null)

            assertEquals(
                listOf(
                    resource(R.string.aether_log_outer_key_ready, "a1b2c3d4…", "172.16.0.2", "2606:4700:110:8a36::1"),
                    resource(R.string.aether_log_inner_key_missing),
                ),
                ServerAetherViewModel.identityLines(status)
            )
        }
    }

    @Test
    fun aCancelledScanStaysBusyUntilItHasStopped() {
        val stopped = CompletableDeferred<Unit>()
        var scans = 0
        var clears = 0
        source.clearer = { clears++; true }
        source.scanner = { _, _ ->
            scans++
            try {
                awaitCancellation()
            } finally {
                // The core is still being ended, as a real one takes a moment to.
                withContext(NonCancellable) { stopped.await() }
            }
        }
        val viewModel = viewModel()

        viewModel.scan(profile)
        viewModel.cancelScan()

        assertEquals(AetherScanState.Scanning, viewModel.scanState.value)
        viewModel.scan(profile)
        viewModel.clearPsiphonData()
        viewModel.cancelScan()
        assertEquals(1, scans)
        assertEquals(0, clears)
        assertEquals(1, viewModel.texts().count { it == resource(R.string.aether_log_scan_cancelled) })

        stopped.complete(Unit)

        assertEquals(AetherScanState.Idle, viewModel.scanState.value)
        viewModel.scan(profile)
        assertEquals(2, scans)
    }

    @Test
    fun aLiveSessionIsReportedWhenTheScreenOpens() {
        source.session = AetherSession(AetherProtocol.MASQUE)

        assertEquals(AetherSession(AetherProtocol.MASQUE), viewModel().session.value)
    }

    @Test
    fun theExitCountriesOnOfferComeFromTheServerList() {
        assertTrue(viewModel().psiphonRegions.value.isEmpty())
        source.regions = listOf("DE", "US")
        assertEquals(listOf("DE", "US"), viewModel().psiphonRegions.value)
    }

    @Test
    fun psiphonDataIsNotClearedUnderALiveSessionAndTheLogTellsTheOutcome() {
        var clears = 0
        source.clearer = { clears++; true }
        val viewModel = viewModel()

        source.session = AetherSession(AetherProtocol.MASQUE)
        viewModel.clearPsiphonData()
        assertEquals(0, clears)
        val blocked = viewModel.log.value.single()
        assertEquals(resource(R.string.aether_psiphon_clear_blocked), blocked.text)
        assertEquals(Log.WARN, blocked.priority)

        source.session = null
        viewModel.clearPsiphonData()
        assertEquals(1, clears)
        val cleared = viewModel.log.value.last()
        assertEquals(resource(R.string.aether_log_psiphon_cleared), cleared.text)
        assertEquals(Log.INFO, cleared.priority)

        source.clearer = { clears++; false }
        viewModel.clearPsiphonData()
        assertEquals(2, clears)
        val failed = viewModel.log.value.last()
        assertEquals(resource(R.string.aether_log_psiphon_clear_failed), failed.text)
        assertEquals(Log.ERROR, failed.priority)
    }

    @Test
    fun aScanIsNotStartedOnTheKeyOfALiveSession() {
        var scans = 0
        source.scanner = { _, _ -> scans++; found }
        val viewModel = viewModel()

        source.session = AetherSession(AetherProtocol.MASQUE)
        viewModel.scan(profile)

        assertEquals(0, scans)
        assertEquals(AetherScanState.Idle, viewModel.scanState.value)
        assertEquals(AetherSession(AetherProtocol.MASQUE), viewModel.session.value)
        val blocked = viewModel.log.value.single()
        assertEquals(resource(R.string.aether_scan_blocked), blocked.text)
        assertEquals(Log.WARN, blocked.priority)

        // Only the listener was visible, so the session's key is unknown and the scan stays blocked.
        source.session = AetherSession(protocol = null)
        viewModel.scan(profile)
        assertEquals(0, scans)

        // A WireGuard session uses another key than this MASQUE profile, so the scan goes ahead.
        source.session = AetherSession(AetherProtocol.WIREGUARD)
        viewModel.scan(profile)
        assertEquals(1, scans)
        assertEquals(AetherScanState.Found(found), viewModel.scanState.value)
    }

    @Test
    fun aScanWhoseKeysAreMissingAsksFirstAndGoesAheadWhenToldTo() {
        var scans = 0
        source.scanner = { _, _ -> scans++; found }
        source.missingFiles += AetherIdentityManager.MASQUE_FILE
        val viewModel = viewModel()
        assertNull(viewModel.keysCheck.value)

        viewModel.scan(profile)
        assertEquals(0, scans)
        assertEquals(AetherScanState.Idle, viewModel.scanState.value)
        assertEquals(AetherKeysCheck.Missing(scan = true), viewModel.keysCheck.value)
        assertTrue(viewModel.log.value.isEmpty())

        viewModel.onKeysCheckHandled()
        assertNull(viewModel.keysCheck.value)
        viewModel.scan(profile, anyway = true)
        assertEquals(1, scans)
        assertNull(viewModel.keysCheck.value)
        assertEquals(AetherScanState.Found(found), viewModel.scanState.value)
    }

    @Test
    fun aScanNeedsOnlyTheKeysOfItsProtocol() {
        var scans = 0
        source.scanner = { _, _ -> scans++; null }
        source.missingFiles += listOf(AetherIdentityManager.MASQUE_FILE, AetherIdentityManager.MASQUE_INNER_FILE)
        val viewModel = viewModel()

        viewModel.scan(profile.copy(aetherProtocol = AetherProtocol.WIREGUARD.type))
        assertEquals(1, scans)
        assertNull(viewModel.keysCheck.value)

        viewModel.onScanHandled()
        viewModel.scan(profile.copy(aetherProtocol = AetherProtocol.MIM.type))
        assertEquals(1, scans)
        assertEquals(AetherKeysCheck.Missing(scan = true), viewModel.keysCheck.value)
    }

    @Test
    fun aSaveGoesOnOnlyWhenTheKeysTheProfileNeedsAreThere() {
        val viewModel = viewModel()

        viewModel.checkKeysBeforeSave(profile)
        assertEquals(AetherKeysCheck.SaveReady, viewModel.keysCheck.value)
        viewModel.onKeysCheckHandled()
        assertNull(viewModel.keysCheck.value)

        // A MASQUE profile needs no inner hop key; masque-in-masque does.
        source.missingFiles += AetherIdentityManager.MASQUE_INNER_FILE
        viewModel.checkKeysBeforeSave(profile)
        assertEquals(AetherKeysCheck.SaveReady, viewModel.keysCheck.value)
        viewModel.checkKeysBeforeSave(profile.copy(aetherProtocol = AetherProtocol.MIM.type))
        assertEquals(AetherKeysCheck.Missing(scan = false), viewModel.keysCheck.value)

        // A command written by hand counts as it runs.
        source.missingFiles += AetherIdentityManager.WIREGUARD_FILE
        viewModel.checkKeysBeforeSave(profile.copy(aetherCommand = "aether --bind 127.0.0.1:10819 --protocol wg"))
        assertEquals(AetherKeysCheck.Missing(scan = false), viewModel.keysCheck.value)

        // Psiphon alone needs no WARP key at all.
        source.missingFiles += AetherIdentityManager.KEY_FILES
        viewModel.checkKeysBeforeSave(profile.copy(aetherPsiphon = "only"))
        assertEquals(AetherKeysCheck.SaveReady, viewModel.keysCheck.value)
    }

    @Test
    fun theLogKeepsOnlyTheLatestEntries() {
        source.scanner = { _, onOutput ->
            repeat(ServerAetherViewModel.LOG_CAPACITY + 50) { onOutput("line $it") }
            null
        }
        val viewModel = viewModel()

        viewModel.scan(profile)

        val entries = viewModel.log.value
        assertEquals(ServerAetherViewModel.LOG_CAPACITY, entries.size)
        assertEquals(entries.map { it.id }.sorted(), entries.map { it.id })
        assertTrue(entries.none { it.text == AetherLogText.Raw("line 0") })
    }
}

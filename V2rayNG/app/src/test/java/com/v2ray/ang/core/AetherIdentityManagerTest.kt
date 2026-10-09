package com.v2ray.ang.core

import com.v2ray.ang.enums.AetherKeyKind
import com.v2ray.ang.enums.AetherProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit

class AetherIdentityManagerTest {

    @TempDir
    lateinit var folder: File

    private fun newFolder(name: String): File = File(folder, name).apply { check(mkdir()) { "could not create $this" } }

    private val quotes = "\"\"\""

    private fun keyFile(deviceId: String, ipv4: String = "172.16.0.2", ipv6: String = "2606:4700:110:8a36::1") =
        listOf(
            "device_id = \"$deviceId\"",
            "access_token = \"secret-token\"",
            "cert_pem = $quotes",
            "-----BEGIN CERTIFICATE-----",
            "device_id = \"hidden-in-pem\"",
            "-----END CERTIFICATE-----",
            quotes,
            "cert_issued_at = 1757580000",
            "ipv4 = \"$ipv4\"",
            "ipv6 = \"$ipv6\"",
            "wg_private_key = \"c2VjcmV0\"",
        ).joinToString("\n")

    private fun workDir(vararg files: Pair<String, String>): File =
        newFolder("aether").apply {
            files.forEach { (name, text) -> File(this, name).writeText(text) }
        }

    @Test
    fun onlyTheIdentityFieldsAreReadFromAKeyFile() {
        val identity = AetherIdentityManager.parse(keyFile("a1b2c3d4-e5f6"))
        assertEquals(AetherIdentity("a1b2c3d4-e5f6", "172.16.0.2", "2606:4700:110:8a36::1"), identity)
    }

    @Test
    fun aFileWithoutADeviceIsNotAKey() {
        assertNull(AetherIdentityManager.parse("ipv4 = \"172.16.0.2\""))
        assertNull(AetherIdentityManager.parse("device_id = \"\""))
        assertNull(AetherIdentityManager.parse(""))
    }

    @Test
    fun eachProtocolReadsItsOwnKeyFiles() {
        val dir = workDir(
            AetherIdentityManager.MASQUE_FILE to keyFile("masque"),
            AetherIdentityManager.MASQUE_INNER_FILE to keyFile("masque-inner"),
            AetherIdentityManager.WIREGUARD_FILE to keyFile("outer"),
            AetherIdentityManager.WIREGUARD_INNER_FILE to keyFile("inner"),
        )

        assertEquals("masque", AetherIdentityManager.status(dir, AetherProtocol.MASQUE).primary?.deviceId)
        assertNull(AetherIdentityManager.status(dir, AetherProtocol.MASQUE).secondary)
        assertEquals("outer", AetherIdentityManager.status(dir, AetherProtocol.WIREGUARD).primary?.deviceId)

        val gool = AetherIdentityManager.status(dir, AetherProtocol.GOOL)
        assertEquals("outer", gool.primary?.deviceId)
        assertEquals("inner", gool.secondary?.deviceId)

        val mim = AetherIdentityManager.status(dir, AetherProtocol.MIM)
        assertEquals("masque", mim.primary?.deviceId)
        assertEquals("masque-inner", mim.secondary?.deviceId)
    }

    @Test
    fun aMissingKeyFileMeansNoKey() {
        val dir = workDir(AetherIdentityManager.WIREGUARD_FILE to keyFile("outer"))

        assertNull(AetherIdentityManager.status(dir, AetherProtocol.MASQUE).primary)
        assertNull(AetherIdentityManager.status(dir, AetherProtocol.GOOL).secondary)
        assertNull(AetherIdentityManager.status(dir, AetherProtocol.MIM).primary)
        assertNull(AetherIdentityManager.status(dir, AetherProtocol.MIM).secondary)
        assertNull(AetherIdentityManager.status(File(folder, "absent"), AetherProtocol.WIREGUARD).primary)
    }

    @Test
    fun theTunnelsOverMasqueShareAKeyAndTheOthersAnother() {
        assertTrue(AetherIdentityManager.sharesIdentity(AetherProtocol.MASQUE, AetherProtocol.MASQUE))
        assertTrue(AetherIdentityManager.sharesIdentity(AetherProtocol.MASQUE, AetherProtocol.MIM))
        assertTrue(AetherIdentityManager.sharesIdentity(AetherProtocol.MIM, AetherProtocol.MIM))
        assertTrue(AetherIdentityManager.sharesIdentity(AetherProtocol.WIREGUARD, AetherProtocol.GOOL))
        assertTrue(AetherIdentityManager.sharesIdentity(AetherProtocol.GOOL, AetherProtocol.GOOL))
        assertFalse(AetherIdentityManager.sharesIdentity(AetherProtocol.MASQUE, AetherProtocol.WIREGUARD))
        assertFalse(AetherIdentityManager.sharesIdentity(AetherProtocol.GOOL, AetherProtocol.MASQUE))
        assertFalse(AetherIdentityManager.sharesIdentity(AetherProtocol.MIM, AetherProtocol.GOOL))
        assertFalse(AetherIdentityManager.sharesIdentity(AetherProtocol.WIREGUARD, AetherProtocol.MIM))
    }

    @Test
    fun theCoreSaysWhenEveryKeyIsRegistered() {
        val done = "[2026-10-01T10:00:00.000Z INFO  aether] [+] identities ready: wireguard, wireguard inner, masque, masque inner"
        assertTrue(AetherIdentityManager.isRegistered(done))

        // Each key is announced as it is saved; only the last line ends the run.
        val one = "[2026-10-01T10:00:00.000Z INFO  aether] [+] masque identity ready: device=a1b2 ipv4=172.16.0.2 ipv6=2606::1"
        assertFalse(AetherIdentityManager.isRegistered(one))
        assertFalse(AetherIdentityManager.isRegistered("[+] no masque identity found; provisioning dedicated masque account"))
    }

    /** The device of each key in [dir], in the order of [AetherIdentityManager.KEY_FILES]; null for a key that is not there. */
    private fun devices(dir: File): List<String?> =
        AetherIdentityManager.KEY_FILES.map { name ->
            File(dir, name).takeIf { it.isFile }?.let { AetherIdentityManager.parse(it.readText()) }?.deviceId
        }

    private fun every(deviceId: String?): List<String?> = List(AetherIdentityManager.KEY_FILES.size) { deviceId }

    private fun keysInUse(): File = workDir(*AetherIdentityManager.KEY_FILES.map { it to keyFile("old") }.toTypedArray())

    private fun register(renewalDir: File, files: List<String> = AetherIdentityManager.KEY_FILES, deviceId: String = "new") =
        files.forEach { File(renewalDir, it).writeText(keyFile(deviceId)) }

    @Test
    fun aRenewalReplacesEveryKeyOnceAllTheNewOnesAreThere() = runBlocking {
        val dir = keysInUse()
        val renewal = File(folder, "aether-renewal")

        val renewed = AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
            register(renewal)
            true
        }

        assertTrue(renewed)
        assertEquals(every("new"), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun theKeysInUseAreNotTouchedWhileTheNewOnesAreRegistered() = runBlocking {
        val dir = keysInUse()
        val renewal = File(folder, "aether-renewal")

        AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
            register(renewal, AetherIdentityManager.KEY_FILES.dropLast(1))
            // Three new keys are there and the last one is still being registered.
            assertEquals(every("old"), devices(dir))
            register(renewal, AetherIdentityManager.KEY_FILES.takeLast(1))
            assertEquals(every("old"), devices(dir))
            true
        }

        assertEquals(every("new"), devices(dir))
    }

    @Test
    fun aMissingNewKeyKeepsEveryOldKey() = runBlocking {
        val dir = keysInUse()
        val renewal = File(folder, "aether-renewal")

        val renewed = AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
            // The runs said they were done, but the inner WireGuard key is not there.
            register(renewal, AetherIdentityManager.KEY_FILES - AetherIdentityManager.WIREGUARD_INNER_FILE)
            true
        }

        assertFalse(renewed)
        assertEquals(every("old"), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun aNewKeyThatDoesNotReadAsAKeyKeepsEveryOldKey() = runBlocking {
        val dir = keysInUse()
        val renewal = File(folder, "aether-renewal")

        val renewed = AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
            register(renewal)
            File(renewal, AetherIdentityManager.MASQUE_INNER_FILE).writeText("device_id = \"\"")
            true
        }

        assertFalse(renewed)
        assertEquals(every("old"), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun aFailedRegistrationKeepsEveryOldKey() = runBlocking {
        val dir = keysInUse()
        val renewal = File(folder, "aether-renewal")

        val renewed = AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
            // The MASQUE keys came, then the WireGuard run failed.
            register(renewal, listOf(AetherIdentityManager.MASQUE_FILE, AetherIdentityManager.MASQUE_INNER_FILE))
            false
        }

        assertFalse(renewed)
        assertEquals(every("old"), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun aCancelledRenewalKeepsEveryOldKey() = runBlocking {
        val dir = keysInUse()
        val renewal = File(folder, "aether-renewal")
        val registering = CompletableDeferred<Unit>()

        val job = launch {
            AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
                register(renewal)
                registering.complete(Unit)
                awaitCancellation()
            }
        }
        registering.await()
        job.cancelAndJoin()

        assertEquals(every("old"), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun aCancellationLandingRightAfterTheNewKeysWereMarkedReadyStillPutsThemInPlace() = runBlocking {
        val dir = keysInUse()
        val renewal = File(folder, "aether-renewal")
        val registered = CompletableDeferred<Unit>()

        val job = launch {
            AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
                register(renewal)
                registered.complete(Unit)
                true
            }
        }
        registered.await()
        // The mark is written on the IO dispatcher. Waiting for it without suspending keeps this
        // single-threaded event loop busy, so the step's result can only be delivered after the
        // cancellation below: the moment a renewal must neither drop its new keys nor leave half of them.
        val mark = File(renewal, AetherIdentityManager.READY_MARK)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!mark.exists() && System.nanoTime() < deadline) Thread.sleep(1)
        assertTrue(mark.exists())
        job.cancel()
        job.join()

        assertEquals(every("new"), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun aRenewalStoppedWhileItMovedTheKeysIsFinishedLater() {
        val dir = keysInUse()
        val renewal = newFolder("aether-renewal")
        register(renewal)
        File(renewal, AetherIdentityManager.READY_MARK).createNewFile()
        // The app was killed after the first two new keys were moved.
        AetherIdentityManager.KEY_FILES.take(2).forEach { File(renewal, it).renameTo(File(dir, it)) }

        assertTrue(AetherIdentityManager.settle(dir, renewal))

        assertEquals(every("new"), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun theKeysARenewalOfAnOlderVersionLeftGoAndTheKeysInUseStay() {
        // That renewal moved the whole identity folder aside to aether-previous while it registered new keys; an app
        // killed meanwhile left it there, which nothing reads.
        val dir = keysInUse()
        File(dir, "aether-wg-lastconn.toml").writeText("peer = \"162.159.192.1:2408\"")
        val previous = newFolder("aether-previous")
        register(previous, deviceId = "older")
        File(previous, AetherIdentityManager.BASE_FILE).writeText("older")

        assertTrue(AetherIdentityManager.settleIn(folder))

        assertFalse(previous.exists())
        assertEquals(every("old"), devices(dir))
        assertEquals("peer = \"162.159.192.1:2408\"", File(dir, "aether-wg-lastconn.toml").readText())
    }

    @Test
    fun settlingTheFilesFolderFinishesARenewalWhoseKeysWereAllReady() {
        val dir = keysInUse()
        val renewal = newFolder("aether-renewal")
        register(renewal)
        File(renewal, AetherIdentityManager.READY_MARK).createNewFile()

        assertTrue(AetherIdentityManager.settleIn(folder))

        assertEquals(every("new"), devices(dir))
        assertEquals(listOf("aether"), folder.list()?.toList())
    }

    @Test
    fun newKeysNotMarkedReadyAreNeverMovedIntoPlace() {
        val dir = keysInUse()
        val renewal = newFolder("aether-renewal")
        // A renewal is still at work here, or one was stopped before it had checked its keys.
        register(renewal)

        assertTrue(AetherIdentityManager.settle(dir, renewal))

        assertEquals(every("old"), devices(dir))
        assertEquals(every("new"), devices(renewal))
    }

    @Test
    fun whatAnInterruptedRenewalLeftIsNotTakenForNewKeys() = runBlocking {
        val dir = keysInUse()
        val renewal = newFolder("aether-renewal")
        register(renewal, AetherIdentityManager.KEY_FILES.take(3), deviceId = "stale")

        val renewed = AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
            // The stale keys are gone before the core runs, which registers all four anew.
            assertEquals(every(null), devices(renewal))
            register(renewal)
            true
        }

        assertTrue(renewed)
        assertEquals(every("new"), devices(dir))
    }

    @Test
    fun aFirstRenewalPutsEveryKeyInPlace() = runBlocking {
        val dir = File(folder, "aether")
        val renewal = File(folder, "aether-renewal")

        val renewed = AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
            register(renewal)
            true
        }

        assertTrue(renewed)
        assertEquals(every("new"), devices(dir))
    }

    @Test
    fun aFailedFirstRenewalLeavesNoKeyBehind() = runBlocking {
        val dir = File(folder, "aether")
        val renewal = File(folder, "aether-renewal")

        val renewed = AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
            register(renewal, listOf(AetherIdentityManager.MASQUE_FILE))
            false
        }

        assertFalse(renewed)
        assertEquals(every(null), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun eachKindRegistersTheKeysOfItsProtocols() {
        val wireguard = AetherIdentityManager.WIREGUARD_FILE
        val wireguardInner = AetherIdentityManager.WIREGUARD_INNER_FILE
        val masque = AetherIdentityManager.MASQUE_FILE
        val masqueInner = AetherIdentityManager.MASQUE_INNER_FILE
        assertEquals(AetherIdentityManager.KEY_FILES, AetherIdentityManager.filesOf(AetherKeyKind.ALL))
        assertEquals(listOf(wireguard), AetherIdentityManager.filesOf(AetherKeyKind.WIREGUARD))
        assertEquals(listOf(masque), AetherIdentityManager.filesOf(AetherKeyKind.MASQUE))
        assertEquals(listOf(wireguard, wireguardInner), AetherIdentityManager.filesOf(AetherKeyKind.GOOL))
        assertEquals(listOf(masque, masqueInner), AetherIdentityManager.filesOf(AetherKeyKind.MIM))

        // A tunnel uses the keys its protocol registers, its outer hop the key of the one-hop protocol.
        for (protocol in AetherProtocol.entries) {
            val kind = AetherKeyKind.entries.single { it.type == protocol.type }
            assertEquals(AetherIdentityManager.filesOf(kind), AetherIdentityManager.filesOf(protocol))
        }
    }

    @Test
    fun theKeysAreReadFileByFile() {
        val dir = workDir(
            AetherIdentityManager.WIREGUARD_FILE to keyFile("outer"),
            AetherIdentityManager.MASQUE_INNER_FILE to keyFile("masque-inner"),
            AetherIdentityManager.MASQUE_FILE to "device_id = \"\"",
        )

        assertEquals(listOf("outer", null, null, "masque-inner"), AetherIdentityManager.keys(dir, AetherIdentityManager.KEY_FILES).map { it.identity?.deviceId })
        assertEquals(AetherIdentityManager.KEY_FILES, AetherIdentityManager.keys(dir, AetherIdentityManager.KEY_FILES).map { it.file })
    }

    @Test
    fun aRenewalOfOneKindReplacesItsKeysAndLeavesTheOthersAlone() = runBlocking {
        for (kind in AetherKeyKind.entries) {
            val dir = keysInUse()
            val renewal = File(folder, "aether-renewal")
            val files = AetherIdentityManager.filesOf(kind)

            val renewed = AetherIdentityManager.renew(dir, renewal, files) {
                register(renewal, files)
                true
            }

            assertTrue(renewed, kind.type)
            assertEquals(AetherIdentityManager.KEY_FILES.map { if (it in files) "new" else "old" }, devices(dir), kind.type)
            assertFalse(renewal.exists(), kind.type)
            dir.deleteRecursively()
        }
    }

    @Test
    fun aKeyOutsideTheKindIsNeverPutInPlace() = runBlocking {
        val dir = keysInUse()
        val renewal = File(folder, "aether-renewal")

        val renewed = AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.filesOf(AetherKeyKind.WIREGUARD)) {
            // The run left a MASQUE key beside the WireGuard key it was asked for.
            register(renewal, listOf(AetherIdentityManager.WIREGUARD_FILE, AetherIdentityManager.MASQUE_FILE))
            true
        }

        assertTrue(renewed)
        assertEquals(listOf("new", "old", "old", "old"), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun aMissingNewKeyOfAKindKeepsEveryOldKeyOfIt() = runBlocking {
        val dir = keysInUse()
        val renewal = File(folder, "aether-renewal")

        val renewed = AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.filesOf(AetherKeyKind.GOOL)) {
            // The outer hop key came; the inner one did not.
            register(renewal, listOf(AetherIdentityManager.WIREGUARD_FILE))
            true
        }

        assertFalse(renewed)
        assertEquals(every("old"), devices(dir))
        assertFalse(renewal.exists())
    }

    @Test
    fun aCoreNeedsTheKeysOfItsProtocolAndNoneWithPsiphonOrTorAlone() {
        fun needed(vararg arguments: String) = AetherIdentityManager.filesNeededBy(arguments.toList())
        val masque = AetherIdentityManager.MASQUE_FILE
        val wireguard = AetherIdentityManager.WIREGUARD_FILE
        assertEquals(listOf(masque), needed("--protocol", "masque", "--h2"))
        assertEquals(listOf(wireguard), needed("--protocol", "wg", "--tor"))
        assertEquals(listOf(wireguard, AetherIdentityManager.WIREGUARD_INNER_FILE), needed("--wiw-outer", "162.159.192.1:2408"))
        assertEquals(listOf(masque, AetherIdentityManager.MASQUE_INNER_FILE), needed("--protocol", "mim", "--psiphon-reverse"))
        // With no protocol named the core runs MASQUE.
        assertEquals(listOf(masque), needed())
        assertEquals(emptyList<String>(), needed("--protocol", "wg", "--psiphon-only"))
        assertEquals(emptyList<String>(), needed("--tor-only"))
    }

    @Test
    fun aKeyThatIsNotThereOrDoesNotReadAsOneIsMissing() {
        val dir = workDir(
            AetherIdentityManager.MASQUE_FILE to keyFile("masque"),
            AetherIdentityManager.MASQUE_INNER_FILE to "device_id = \"\"",
        )

        assertEquals(emptyList<String>(), AetherIdentityManager.missing(dir, listOf(AetherIdentityManager.MASQUE_FILE)))
        assertEquals(
            listOf(AetherIdentityManager.MASQUE_INNER_FILE, AetherIdentityManager.WIREGUARD_FILE),
            AetherIdentityManager.missing(dir, AetherIdentityManager.filesOf(AetherKeyKind.MIM) + AetherIdentityManager.WIREGUARD_FILE)
        )
        assertEquals(emptyList<String>(), AetherIdentityManager.missing(dir, emptyList()))
    }

    @Test
    fun aRenewalLeavesEverythingButTheKeysAlone() = runBlocking {
        val dir = keysInUse()
        File(dir, "aether-wg-lastconn.toml").writeText("peer = \"162.159.192.1:2408\"")
        File(dir, "aether-tor").mkdirs()
        File(dir, "aether-tor/state").writeText("guards")
        val renewal = File(folder, "aether-renewal")

        AetherIdentityManager.renew(dir, renewal, AetherIdentityManager.KEY_FILES) {
            register(renewal)
            true
        }

        assertEquals(every("new"), devices(dir))
        assertEquals("peer = \"162.159.192.1:2408\"", File(dir, "aether-wg-lastconn.toml").readText())
        assertEquals("guards", File(dir, "aether-tor/state").readText())
    }
}

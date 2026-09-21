package com.v2ray.ang.shizuku

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.main.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Device regression tests: grant VPN/Shizuku permission and select a working profile first.
 * Run with -e backend native|hev and --no-hidden-api-checks for the fault injector only.
 * Disable client Wi-Fi first so hotspot setup does not independently restart the main VPN.
 */
@SdkSuppress(minSdkVersion = 33)
class TetheringLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val args = ShizukuTetheringService.createUserServiceArgs()
    private lateinit var service: IShizukuTetheringService
    private lateinit var snapshot: HotspotRoutingSnapshot
    private lateinit var lease: ICoreTetheringLease
    private val connected = CountDownLatch(1)
    private var activity: ActivityScenario<MainActivity>? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IShizukuTetheringService.Stub.asInterface(binder)
            connected.countDown()
        }
        override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    @Before
    fun connectToRunningCore() {
        InstrumentationRegistry.getArguments().getString("backend")?.let { backend ->
            require(backend == "native" || backend == "hev")
            MmkvManager.encodeSettings(AppConfig.PREF_MODE, AppConfig.VPN)
            MmkvManager.encodeSettings(AppConfig.PREF_USE_HEV_TUNNEL, backend == "hev")
            if (backend == "hev") MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
        }
        activity = ActivityScenario.launch(MainActivity::class.java)
        LauncherManager.startService(context)
        instrumentation.runOnMainSync { Shizuku.bindUserService(args, connection) }
        assertTrue("Shizuku UserService did not bind", connected.await(10, TimeUnit.SECONDS))
        assertEquals(0, service.stopRouting())
        val received = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra("key", 0) != AppConfig.MSG_HOTSPOT_CONFIG_RESPONSE) return
                val current = intent.serializable<HotspotRoutingSnapshot>("content") ?: return
                if (!current.running) return
                val currentLease = intent.coreTetheringLease() ?: return
                snapshot = current
                lease = currentLease
                received.countDown()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            // Starting the main core is asynchronous; query until it publishes its launch lease.
            repeat(20) {
                if (received.count == 0L) return@repeat
                MessageHelper.sendMsg2Service(context, AppConfig.MSG_QUERY_HOTSPOT_CONFIG, "")
                received.await(500, TimeUnit.MILLISECONDS)
            }
            assertTrue("Start the main VPN before running this test", received.await(5, TimeUnit.SECONDS))
            assertTrue(snapshot.running && snapshot.vpnMode)
            Log.i("TetheringLifecycleTest", "Testing ${if (snapshot.useHev) "HEV" else "native Xray"} tethering")
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @After
    fun disconnect() {
        if (::service.isInitialized) {
            service.setStatusListener(null)
            assertEquals(0, service.stopRouting())
        }
        instrumentation.runOnMainSync { Shizuku.unbindUserService(args, connection, false) }
        activity?.close()
    }

    private fun start(token: String, coreLease: ICoreTetheringLease = lease): Int {
        val parameters = HotspotRoutingConfig.parametersFromSnapshot(snapshot)
        return service.startRouting(
            parameters.useHev, parameters.profileName, parameters.dnsServers.toTypedArray(),
            parameters.ipv6Enabled, parameters.xudpKey, token, parameters.launchId, coreLease,
        )
    }

    @Test
    fun duplicateStartDoesNotTouchEitherLease() {
        val calls = AtomicInteger()
        val countedLease = object : DelegatingLease(lease) {
            override fun openEngineConfig(launchId: String): ParcelFileDescriptor {
                calls.incrementAndGet()
                return super.openEngineConfig(launchId)
            }
            override fun holdTestNetwork(tun: ParcelFileDescriptor) {
                calls.incrementAndGet()
                super.holdTestNetwork(tun)
            }
            override fun releaseTestNetwork() {
                calls.incrementAndGet()
                super.releaseTestNetwork()
            }
        }
        assertEquals(0, start(UUID.randomUUID().toString(), countedLease))
        calls.set(0)
        assertEquals(ShizukuTetheringService.RESULT_ALREADY_ACTIVE, start(UUID.randomUUID().toString(), countedLease))
        assertEquals(0, calls.get())
        assertTrue(service.getStatus(false).hasRoutingSession)
    }

    @Test
    fun lateCoreUpdateCannotUndoExplicitStop() {
        val token = UUID.randomUUID().toString()
        assertEquals(0, start(token))
        assertEquals(0, service.stopRouting())
        val applied = CountDownLatch(1)
        service.setStatusListener(object : ITetheringStatusListener.Stub() {
            override fun onStatusChanged() { applied.countDown() }
        })
        val parameters = HotspotRoutingConfig.parametersFromSnapshot(snapshot)
        service.synchronizeRouting(
            token, parameters.useHev, parameters.profileName, parameters.dnsServers.toTypedArray(),
            parameters.ipv6Enabled, parameters.xudpKey, parameters.launchId, lease,
        )
        assertTrue(applied.await(5, TimeUnit.SECONDS))
        assertFalse(service.getStatus(false).hasRoutingSession)
    }

    @Test
    fun listenerRemovalDoesNotWaitForBlockedStartup() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blockingLease = object : DelegatingLease(lease) {
            override fun openEngineConfig(launchId: String): ParcelFileDescriptor {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                return super.openEngineConfig(launchId)
            }
        }
        val worker = Executors.newSingleThreadExecutor()
        try {
            val start = worker.submit<Int> { start(UUID.randomUUID().toString(), blockingLease) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val before = System.nanoTime()
            service.setStatusListener(null)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 1000)
            release.countDown()
            assertEquals(0, start.get(30, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    @Test
    fun staleLaunchCannotReadCurrentConfiguration() {
        assertFalse(lease.isCurrentLaunch("obsolete-launch"))
        assertThrows(Exception::class.java) { lease.openEngineConfig("obsolete-launch") }
        assertTrue(lease.isCurrentLaunch(requireNotNull(snapshot.launchId)))
        lease.openEngineConfig(snapshot.launchId).use { assertTrue(it.statSize > 0) }
    }

    @Test
    fun exitedHevWorkerIsReportedAndCanStillBeStopped() {
        assumeTrue(snapshot.useHev)
        val invalidConfig = object : DelegatingLease(lease) {
            override fun openEngineConfig(launchId: String): ParcelFileDescriptor {
                val file = File.createTempFile("invalid-hev-", ".yaml", context.cacheDir)
                try {
                    file.writeText("this is not a HEV configuration")
                    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } finally {
                    file.delete()
                }
            }
        }
        val result = start(UUID.randomUUID().toString(), invalidConfig)
        assertTrue(result == 0 || result == ShizukuTetheringService.RESULT_ROUTING_FAILED)
        awaitStatus { it.routingState == ShizukuTetheringService.ROUTING_STATE_ERROR }
        if (result == 0) assertTrue(service.getStatus(false).hasRoutingSession)
        assertEquals(0, service.stopRouting())
        assertFalse(service.getStatus(false).hasRoutingSession)
    }

    @Test
    fun coreStopKeepsHotspotOnDeadTunAndRestartResumesIt() {
        val token = UUID.randomUUID().toString()
        assertEquals(0, start(token))
        assertEquals(0, service.setWifiHotspotEnabled(true))
        val before = service.getStatus(false)
        assertEquals(ShizukuTetheringService.RESULT_OK, before.warning)
        assertEquals(1, before.activeTetheringTypes and 1)
        assertTrue(before.routingDetail.startsWith("testtun"))
        service.notifyCoreStopping(token)
        awaitStatus { it.routingState == ShizukuTetheringService.ROUTING_STATE_WAITING }
        val stopped = service.getStatus(false)
        assertEquals(before.routingDetail, stopped.routingDetail)
        assertEquals(before.activeTetheringTypes, stopped.activeTetheringTypes)
        val parameters = HotspotRoutingConfig.parametersFromSnapshot(snapshot)
        service.synchronizeRouting(
            token, parameters.useHev, parameters.profileName, parameters.dnsServers.toTypedArray(),
            parameters.ipv6Enabled, parameters.xudpKey, parameters.launchId, lease,
        )
        awaitStatus { it.routingState == before.routingState }
        assertEquals(before.activeTetheringTypes, service.getStatus(false).activeTetheringTypes)
    }

    @Test
    fun unsafeUpstreamStopsHotspotEvenWhileConfigurationReadIsBlocked() {
        val token = UUID.randomUUID().toString()
        assertEquals(0, start(token))
        assertEquals(0, service.setWifiHotspotEnabled(true))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blockingLease = object : DelegatingLease(lease) {
            override fun openEngineConfig(launchId: String): ParcelFileDescriptor {
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS))
                return super.openEngineConfig(launchId)
            }
        }
        instrumentation.uiAutomation.adoptShellPermissionIdentity()
        var observer: TetheringUpstreamMonitor? = null
        try {
            val manager = requireNotNull(context.getSystemService("tethering"))
            val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
            observer = if (usesPublicTetheringApi()) {
                TetheringApi36.observeUpstream(manager, connectivity, Runnable::run) {}
            } else {
                TetheringPlatformCompat.observeUpstreamLegacy(manager, connectivity, Runnable::run) {}
            }
            assertTrue(observer.awaitInterfaces(5).isNotEmpty())
            val parameters = HotspotRoutingConfig.parametersFromSnapshot(snapshot)
            service.synchronizeRouting(
                token, parameters.useHev, parameters.profileName, parameters.dnsServers.toTypedArray(),
                parameters.ipv6Enabled, parameters.xudpKey, parameters.launchId, blockingLease,
            )
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            manager.javaClass.getMethod("setPreferTestNetworks", Boolean::class.javaPrimitiveType).invoke(manager, false)
            // The preference only affects the next upstream selection. Start a downstream from
            // outside our service while its lifecycle is blocked to force that selection now.
            if (usesPublicTetheringApi()) {
                assertEquals(0, TetheringApi36.stopTethering(manager, 0, Runnable::run, 5))
            } else {
                assertEquals(0, TetheringPlatformCompat.stopTethering(manager, 0))
            }
            val stoppedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (observer.awaitInterfaces(2).isNotEmpty() && System.nanoTime() < stoppedDeadline) Thread.sleep(100)
            assertTrue(observer.awaitInterfaces(2).isEmpty())
            val startResult = if (usesPublicTetheringApi()) {
                TetheringApi36.startTethering(manager, 0, Runnable::run, 5)
            } else {
                TetheringPlatformCompat.startTethering(manager, 0, Runnable::run, 5)
            }
            assertEquals(0, startResult)
            Thread.sleep(1000)
            // Read Android's independent observer, not getStatus(), which correctly serializes
            // with the deliberately blocked lifecycle operation under test.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            while (observer.awaitInterfaces(2).isNotEmpty() && System.nanoTime() < deadline) Thread.sleep(100)
            assertTrue("Unsafe hotspot remained active behind the lifecycle lock", observer.awaitInterfaces(2).isEmpty())
            release.countDown()
            assertEquals(ShizukuTetheringService.RESULT_UNPROTECTED_UPSTREAM, service.getStatus(false).warning)
            assertEquals(ShizukuTetheringService.RESULT_OK, service.getStatus(false).warning)
        } finally {
            release.countDown()
            observer?.close()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun awaitStatus(matches: (TetheringStatusSnapshot) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (matches(service.getStatus(false))) return
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for tethering status")
    }

    private open class DelegatingLease(private val delegate: ICoreTetheringLease) : ICoreTetheringLease.Stub() {
        override fun openEngineConfig(launchId: String): ParcelFileDescriptor = delegate.openEngineConfig(launchId)
        override fun isCurrentLaunch(launchId: String): Boolean = delegate.isCurrentLaunch(launchId)
        override fun holdTestNetwork(tun: ParcelFileDescriptor) = delegate.holdTestNetwork(tun)
        override fun releaseTestNetwork() = delegate.releaseTestNetwork()
        override fun assetFingerprint(): String = delegate.assetFingerprint()
        override fun listAssetFiles(): Array<String> = delegate.listAssetFiles()
        override fun openAssetFile(name: String): ParcelFileDescriptor = delegate.openAssetFile(name)
    }
}

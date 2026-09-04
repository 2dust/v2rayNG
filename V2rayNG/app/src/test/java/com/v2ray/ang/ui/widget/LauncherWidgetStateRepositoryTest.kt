package com.v2ray.ang.ui.widget

import com.v2ray.ang.core.ConnectionTestSession
import com.v2ray.ang.dto.ConnectionTestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class LauncherWidgetStateRepositoryTest {
    @Test
    fun importingFirstProfileReplacesEmptyStateAfterStorageRefresh() = runBlocking {
        var selected: WidgetProfile? = null
        val session = ConnectionTestSession()
        val repository = LauncherWidgetStateRepository({ selected }, session.state)
        assertNull(repository.refresh().profile)

        selected = WidgetProfile("imported", "Imported profile")
        repository.refresh()

        assertEquals(selected, repository.states.first().profile)
        assertFalse(repository.states.first().isRunning)
    }

    @Test
    fun replacementsRenamesAndDeletionAreObservedWithoutServiceEvents() = runBlocking {
        var selected: WidgetProfile? = WidgetProfile("old", "Old name")
        val repository = LauncherWidgetStateRepository({ selected }, ConnectionTestSession().state)
        repository.refresh()
        selected = WidgetProfile("new", "Replacement")
        assertEquals(selected, repository.refresh().profile)
        selected = selected.copy(name = "Renamed")
        assertEquals("Renamed", repository.refresh().profile?.name)
        selected = null
        assertNull(repository.refresh().profile)
    }

    @Test
    fun refreshCannotRestoreRunningStateAfterStop() = runBlocking {
        val session = ConnectionTestSession()
        val repository = LauncherWidgetStateRepository({ WidgetProfile("a", "A") }, session.state)
        session.started("a")
        assertTrue(repository.refresh().isConnected)
        session.stopped()
        repeat(3) { assertFalse(repository.refresh().isRunning) }
    }

    @Test
    fun profileSwitchHidesPreviousResultAndDisablesTestingUntilCoreSwitches() = runBlocking {
        val session = ConnectionTestSession()
        var selected = WidgetProfile("a", "A")
        val repository = LauncherWidgetStateRepository({ selected }, session.state)
        session.started("a")
        session.complete(session.beginTest()!!, ConnectionTestResult(42, country = "DE"))
        assertEquals(42L, repository.refresh().result?.delayMillis)

        selected = WidgetProfile("b", "B")
        val state = repository.refresh()
        assertTrue(state.isRunning) // Stop still controls the running A service.
        assertFalse(state.isConnected)
        assertFalse(state.canTest)
        assertNull(state.result)

        session.started("b")
        assertTrue(repository.states.first().canTest)
    }

    @Test
    fun recreatedWidgetRetainsCompletedResultButNewDaemonDoesNot() = runBlocking {
        val session = ConnectionTestSession()
        session.started("a")
        session.complete(session.beginTest()!!, ConnectionTestResult(42, country = "DE"))
        val repository = LauncherWidgetStateRepository({ WidgetProfile("a", "A") }, session.state)
        assertEquals(42L, repository.refresh().result?.delayMillis)

        val newDaemon = LauncherWidgetStateRepository(
            { WidgetProfile("a", "A") }, ConnectionTestSession().state
        )
        assertNull(newDaemon.refresh().result)
        assertFalse(newDaemon.refresh().isTesting)
    }

    @Test
    fun observingPresentationDoesNotRepeatedlyReadStorage() = runBlocking {
        var reads = 0
        val repository = LauncherWidgetStateRepository(
            { reads++; WidgetProfile("a", "A") }, ConnectionTestSession().state
        )
        repository.refresh()
        repeat(4) { repository.states.first() }
        assertEquals(1, reads)
    }

    @Test
    fun failedStorageRefreshKeepsLastSuccessfulProfile() = runBlocking {
        var fail = false
        val repository = LauncherWidgetStateRepository(
            { if (fail) throw IOException("storage unavailable") else WidgetProfile("a", "A") },
            ConnectionTestSession().state,
        )
        repository.refresh()
        fail = true
        var failure: Exception? = null
        runWidgetUpdate({ repository.refresh() }, { failure = it })
        assertTrue(failure is IOException)
        assertEquals("a", repository.states.first().profile?.guid)
    }

    @Test
    fun renderingFailureIsContainedAndNextUpdateCanSucceed() = runBlocking {
        val failures = mutableListOf<Exception>()
        runWidgetUpdate({ throw IOException("widget removed during update") }, failures::add)
        var updated = false
        runWidgetUpdate({ updated = true }, failures::add)
        assertEquals(1, failures.size)
        assertTrue(updated)
    }

    @Test
    fun updateCancellationIsPropagatedWithoutReportingFailure() = runBlocking {
        var reported = false
        try {
            runWidgetUpdate({ throw CancellationException("receiver cancelled") }, { reported = true })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(reported)
        }
    }

    @Test
    fun responsiveSizesAndWidthBreakpointsMatchLayouts() {
        LauncherWidgetLayout.entries.forEach {
            assertTrue(it.size.height.value >= 68f)
        }
        assertEquals(LauncherWidgetLayout.COMPACT, LauncherWidgetLayout.forWidth(85f))
        assertEquals(LauncherWidgetLayout.MEDIUM, LauncherWidgetLayout.forWidth(180f))
        assertEquals(LauncherWidgetLayout.WIDE, LauncherWidgetLayout.forWidth(280f))
        assertEquals(LauncherWidgetLayout.EXTRA_WIDE, LauncherWidgetLayout.forWidth(380f))
    }
}

package com.v2ray.ang.ui.widget

import com.v2ray.ang.core.ConnectionTestSession
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
        assertTrue(repository.refresh().isRunning)
        session.stopped()
        repeat(3) { assertFalse(repository.refresh().isRunning) }
    }

    @Test
    fun profileSwitchShowsSelectedNameWhileStopControlsRunningService() = runBlocking {
        val session = ConnectionTestSession()
        var selected = WidgetProfile("a", "A")
        val repository = LauncherWidgetStateRepository({ selected }, session.state)
        session.started("a")
        assertEquals(selected, repository.refresh().profile)

        selected = WidgetProfile("b", "B")
        val state = repository.refresh()
        assertTrue(state.isRunning) // Stop still controls the running A service.
        assertEquals(selected, state.profile)

        session.started("b")
        assertTrue(repository.states.first().isRunning)
    }

    @Test
    fun recreatedWidgetReadsLiveServiceStateButNewDaemonStartsStopped() = runBlocking {
        val session = ConnectionTestSession()
        session.started("a")
        val repository = LauncherWidgetStateRepository({ WidgetProfile("a", "A") }, session.state)
        assertTrue(repository.refresh().isRunning)

        val newDaemon = LauncherWidgetStateRepository(
            { WidgetProfile("a", "A") }, ConnectionTestSession().state
        )
        assertFalse(newDaemon.refresh().isRunning)
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
        listOf(180f, 280f, 380f, 600f).forEach { width ->
            assertEquals(LauncherWidgetLayout.HORIZONTAL, LauncherWidgetLayout.forWidth(width))
        }
    }

    @Test
    fun fiveColumnPixelGridUsesSameRowAtEveryWidthAboveOneCell() {
        // Pixel Launcher reports available dp, not grid spans. These are rounded down
        // from the 426dp Pixel's five-column grid: 196, 438, 680, 922 and 1164 pixels at 3x.
        val widths = listOf(65f, 146f, 226f, 307f, 388f)
        val layouts = listOf(
            LauncherWidgetLayout.COMPACT,
            LauncherWidgetLayout.HORIZONTAL,
            LauncherWidgetLayout.HORIZONTAL,
            LauncherWidgetLayout.HORIZONTAL,
            LauncherWidgetLayout.HORIZONTAL,
        )
        widths.zip(layouts).forEach { (width, expected) ->
            assertEquals(expected, LauncherWidgetLayout.forWidth(width))
            assertTrue(expected.size.width.value <= width)
        }
    }

    @Test
    fun layoutChangesAtEachDeclaredResponsiveWidth() {
        LauncherWidgetLayout.entries.zipWithNext().forEach { (previous, next) ->
            assertEquals(previous, LauncherWidgetLayout.forWidth(next.minimumWidthDp - 1f))
            assertEquals(next, LauncherWidgetLayout.forWidth(next.minimumWidthDp))
        }
    }
}

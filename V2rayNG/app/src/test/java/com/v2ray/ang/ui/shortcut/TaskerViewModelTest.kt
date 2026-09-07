package com.v2ray.ang.ui.shortcut

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.RemoteControlManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TaskerViewModelTest {
    private val application = mock<Application>()
    private val remoteControl = mock<RemoteControlManager>()
    private val profiles = mock<MmkvManager>()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()): TaskerViewModel {
        whenever(remoteControl.configurationToken(application, "host")).thenReturn("token")
        whenever(profiles.decodeAllServerList()).thenReturn(mutableListOf("profile-id"))
        whenever(profiles.decodeServerConfig("profile-id"))
            .thenReturn(ProfileItem(configType = EConfigType.VMESS, remarks = "Profile"))
        return spy(TaskerViewModel(application, savedState, remoteControl, profiles)).also {
            doReturn("Current profile").whenever(it).getString(R.string.tasker_current_profile)
        }
    }

    @Test fun initialStateDoesNotAuthorizeOrSelectAProfile() = runBlocking {
        val viewModel = TaskerViewModel(mock<Application>(), SavedStateHandle())
        assertTrue(viewModel.items.value.isEmpty())
        assertFalse(viewModel.start.value)
        assertNull(viewModel.selectedGuid.value)
        viewModel.select("unknown")
        assertNull(viewModel.selectedGuid.value)
        assertEquals(TaskerConfiguration.NoSelection, viewModel.configuration("untrusted"))
    }

    @Test fun restoredSelectionUsesGuidAndRetainsSwitchChanges() {
        val saved = SavedStateHandle(mapOf("guid" to "profile-id", "start" to true, "initialized" to true))
        val viewModel = TaskerViewModel(mock<Application>(), saved)
        assertEquals("profile-id", viewModel.selectedGuid.value)
        assertTrue(viewModel.start.value)
        viewModel.setStart(false)
        assertFalse(viewModel.start.value)
        assertEquals(false, saved.get<Boolean>("start"))
    }

    @Test fun initializationPrecedesLoadingAndDoesNotOverwriteEdits() = runBlocking {
        val viewModel = viewModel()
        val loadingProfiles = CompletableDeferred<Unit>()
        val releaseProfiles = CountDownLatch(1)
        whenever(profiles.decodeAllServerList()).thenAnswer {
            loadingProfiles.complete(Unit)
            check(releaseProfiles.await(5, TimeUnit.SECONDS))
            mutableListOf("profile-id")
        }
        val loading = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.load("host", true, "profile-id")
        }
        try {
            loadingProfiles.await()
            assertTrue(viewModel.isLoading.value)
            assertTrue(viewModel.start.value)
            assertEquals("profile-id", viewModel.selectedGuid.value)
            assertEquals(TaskerConfiguration.NoSelection, viewModel.configuration("host"))
            viewModel.setStart(false)
        } finally {
            releaseProfiles.countDown()
        }
        assertTrue(loading.await())
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.start.value)
        assertTrue(viewModel.load("host", true, AppConfig.TASKER_DEFAULT_GUID))
        assertEquals("profile-id", viewModel.selectedGuid.value)
        assertFalse(viewModel.start.value)
    }

    @Test fun deletedProfileRequiresSelectionInsteadOfDenyingAccess() = runBlocking {
        val viewModel = viewModel()
        assertTrue(viewModel.load("host", true, "deleted-profile"))
        assertNull(viewModel.selectedGuid.value)
        assertEquals(TaskerConfiguration.NoSelection, viewModel.configuration("host"))
        viewModel.select("profile-id")
        val result = viewModel.configuration("host") as TaskerConfiguration.Ready
        assertEquals("profile-id", result.item.guid)
        assertTrue(result.start)
        assertEquals("token", result.token)
    }

    @Test fun emptyProfilesAndMissingInputStillOfferCurrentProfile() = runBlocking {
        val viewModel = viewModel()
        whenever(profiles.decodeAllServerList()).thenReturn(mutableListOf())
        assertTrue(viewModel.load("host", null, null))
        assertEquals(listOf(AppConfig.TASKER_DEFAULT_GUID), viewModel.items.value.map { it.guid })
        assertFalse(viewModel.start.value)
        assertEquals(TaskerConfiguration.NoSelection, viewModel.configuration("host"))
        viewModel.select(AppConfig.TASKER_DEFAULT_GUID)
        assertTrue(viewModel.configuration("host") is TaskerConfiguration.Ready)
    }

    @Test fun unauthorizedLoadDoesNotReadProfiles() = runBlocking {
        val viewModel = viewModel()
        whenever(remoteControl.configurationToken(application, "host")).thenReturn(null)
        assertFalse(viewModel.load("host", true, "profile-id"))
        verify(profiles, never()).decodeAllServerList()
        assertTrue(viewModel.items.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }

    @Test fun revocationAfterLoadingReturnsAccessDenied() = runBlocking {
        val viewModel = viewModel()
        assertTrue(viewModel.load("host", true, "profile-id"))
        whenever(remoteControl.configurationToken(application, "host")).thenReturn(null)
        assertEquals(TaskerConfiguration.AccessDenied, viewModel.configuration("host"))
    }

    @Test fun failedOrCancelledLoadAlwaysClearsLoadingState() = runBlocking {
        for (failure in listOf(IllegalStateException("storage unavailable"), CancellationException("cancelled"))) {
            reset(profiles)
            val viewModel = viewModel()
            whenever(profiles.decodeAllServerList()).thenThrow(failure)
            try {
                viewModel.load("host", true, "profile-id")
                fail("Expected load failure")
            } catch (actual: Exception) {
                assertEquals(failure.javaClass, actual.javaClass)
                assertEquals(failure.message, actual.message)
            }
            assertFalse(viewModel.isLoading.value)
        }
    }
}

package com.v2ray.ang.ui.shortcut

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class TaskerViewModelTest {
    @Test fun initialStateDoesNotAuthorizeOrSelectAProfile() = runBlocking {
        val viewModel = TaskerViewModel(mock<Application>(), SavedStateHandle())
        assertTrue(viewModel.items.value.isEmpty())
        assertFalse(viewModel.start.value)
        assertNull(viewModel.selectedGuid.value)
        viewModel.select("unknown")
        assertNull(viewModel.selectedGuid.value)
        assertNull(viewModel.configuration("untrusted"))
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
}

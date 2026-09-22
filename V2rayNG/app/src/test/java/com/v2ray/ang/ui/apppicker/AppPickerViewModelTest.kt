package com.v2ray.ang.ui.apppicker

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class AppPickerViewModelTest {

    @Test
    fun initialize_preservesChangesAfterFirstCall() {
        val viewModel = AppPickerViewModel(mock<Application>())

        viewModel.initialize(listOf("initial.package"))
        viewModel.toggleApp("added.package")
        viewModel.initialize(listOf("initial.package"))

        assertEquals(
            setOf("initial.package", "added.package"),
            viewModel.selectedPackages.value
        )
    }

    @Test
    fun saveRemoteControlSelection_skipsUnchangedSelections() = runBlocking {
        for (initial in listOf(emptySet(), setOf("initial.package"))) {
            val application = mock<Application>()
            val viewModel = loadedRemotePicker(application, initial)

            assertTrue(viewModel.saveRemoteControlSelection())
            verifyNoInteractions(application)
        }
    }

    @Test
    fun saveRemoteControlSelection_skipsRevertedEdits() = runBlocking {
        val application = mock<Application>()
        val viewModel = loadedRemotePicker(application, setOf("initial.package"))
        viewModel.toggleApp("initial.package")
        viewModel.toggleApp("added.package")
        viewModel.toggleApp("initial.package")
        viewModel.toggleApp("added.package")

        assertEquals(setOf("initial.package"), viewModel.selectedPackages.value)
        assertTrue(viewModel.saveRemoteControlSelection())
        verifyNoInteractions(application)
    }

    private fun loadedRemotePicker(application: Application, initial: Set<String>): AppPickerViewModel =
        AppPickerViewModel(application).apply {
            initialize(initial, remoteControl = true)
            // Seed the completed Android load without adding production-only test hooks.
            javaClass.getDeclaredField("selectedSnapshot").apply { isAccessible = true }.set(this, initial)
            javaClass.getDeclaredField("remoteSelectionLoaded").apply { isAccessible = true }.setBoolean(this, true)
        }
}

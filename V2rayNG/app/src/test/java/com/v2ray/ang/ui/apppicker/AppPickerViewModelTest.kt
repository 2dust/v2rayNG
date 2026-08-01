package com.v2ray.ang.ui.apppicker

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

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
}

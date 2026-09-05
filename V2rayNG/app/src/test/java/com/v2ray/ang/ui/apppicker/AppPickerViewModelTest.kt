package com.v2ray.ang.ui.apppicker

import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.repository.AppListRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AppPickerViewModelTest {

    private fun viewModel(selected: List<String> = emptyList()): AppPickerViewModel {
        val repo = mock<AppListRepository> {
            onBlocking { loadApps(any(), any()) } doReturn emptyList()
            on { filter(any(), any()) } doReturn emptyList()
        }
        val handle = SavedStateHandle(
            mapOf(AppRoute.EXTRA_PICKER_SELECTED to ArrayList(selected))
        )
        return AppPickerViewModel(repo, handle)
    }

    private fun AppPickerViewModel.firstEvent(): BaseEvent =
        runBlocking { withTimeout(TIMEOUT_MS) { events.first() } }

    @Test
    fun intentArgumentsSeedTheSelection() {
        val viewModel = viewModel(listOf("com.a", "com.b"))

        assertEquals(setOf("com.a", "com.b"), viewModel.uiState.value.selected)
        assertEquals(AppPickerUiState.DEFAULT_TITLE_RES, viewModel.uiState.value.titleRes)
    }

    @Test
    fun togglingAppAddsThenRemovesIt() {
        val viewModel = viewModel()

        viewModel.onAction(AppPickerAction.ToggleApp("com.a"))
        assertEquals(setOf("com.a"), viewModel.uiState.value.selected)

        viewModel.onAction(AppPickerAction.ToggleApp("com.a"))
        assertEquals(emptySet<String>(), viewModel.uiState.value.selected)
    }

    @Test
    fun closingSearchClearsTheQuery() {
        val viewModel = viewModel()

        viewModel.onAction(AppPickerAction.SearchOpen)
        viewModel.onAction(AppPickerAction.QueryChanged("v2ray"))
        assertEquals("v2ray", viewModel.uiState.value.query)

        viewModel.onAction(AppPickerAction.SearchClose)
        assertEquals("", viewModel.uiState.value.query)
        assertFalse(viewModel.uiState.value.searchActive)
    }

    @Test
    fun backClosesSearchBeforeClosingTheScreen() {
        val viewModel = viewModel()
        viewModel.onAction(AppPickerAction.SearchOpen)

        viewModel.onAction(AppPickerAction.Back)

        assertFalse(viewModel.uiState.value.searchActive)
    }

    @Test
    fun backWithoutChangesReportsCancelled() {
        val viewModel = viewModel(listOf("com.a"))

        viewModel.onAction(AppPickerAction.Back)

        val event = viewModel.firstEvent()
        assertTrue(event is BaseEvent.Finish)
        assertEquals(BaseResult.Cancelled, (event as BaseEvent.Finish).result)
    }

    @Test
    fun backAfterChangesReportsSortedSelection() {
        val viewModel = viewModel(listOf("com.b"))
        viewModel.onAction(AppPickerAction.ToggleApp("com.a"))

        viewModel.onAction(AppPickerAction.Back)

        val event = viewModel.firstEvent()
        assertEquals(
            BaseResult.Selected(listOf("com.a", "com.b")),
            (event as BaseEvent.Finish).result
        )
    }

    private companion object {
        const val TIMEOUT_MS = 2_000L
    }
}

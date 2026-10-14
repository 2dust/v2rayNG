package com.v2ray.ang.ui.server

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.AetherSettings
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class AetherEditorViewModelTest {
    @Test
    fun loadAndSaveFailuresDoNotReportSuccess() = runBlocking {
        val operations = mutableListOf<String>()
        val failedLoad = AetherEditorViewModel(mock<Application>(), SavedStateHandle(), Dispatchers.Unconfined,
            { _, _ -> error("Read failed") }, { _, _ -> error("Must not save") },
            { operation, _, _ -> operations.add(operation) })
        failedLoad.initialize("server-guid", "group-guid")
        withTimeout(3_000) { failedLoad.state.first { !it.loading } }
        assertEquals(R.string.aether_load_error, failedLoad.state.value.errorRes)
        val failedSave = AetherEditorViewModel(mock<Application>(), SavedStateHandle(), Dispatchers.Unconfined,
            { _, _ -> ProfileItem.create(EConfigType.AETHER).copy(remarks = "valid") },
            { _, _ -> error("Write failed") }, { operation, _, _ -> operations.add(operation) })
        failedSave.initialize("server-guid", "group-guid")
        withTimeout(3_000) { failedSave.state.first { !it.loading } }
        failedSave.save()
        withTimeout(3_000) { failedSave.state.first { it.errorRes != null } }
        assertEquals(false, failedSave.state.value.saving)
        assertEquals(R.string.aether_save_error, failedSave.state.value.errorRes)
        assertNull(failedSave.state.value.savedGuid)
        assertEquals(listOf("load", "save"), operations)
    }

    @Test
    fun loadEditAndSavePreserveProfileIdentityAndMetadata() = runBlocking {
        val original = ProfileItem.create(EConfigType.AETHER).copy(remarks = "original", subscriptionId = "group-guid")
        var receivedGuid: String? = null
        var receivedProfile: ProfileItem? = null
        val viewModel = AetherEditorViewModel(mock<Application>(), SavedStateHandle(), Dispatchers.Unconfined,
            { guid, _ -> assertEquals("server-guid", guid); original },
            { guid, profile -> receivedGuid = guid; receivedProfile = profile; guid })
        assertEquals(true, viewModel.state.value.loading)
        viewModel.initialize("server-guid", "another-group")
        withTimeout(3_000) { viewModel.state.first { !it.loading } }
        viewModel.changeRemarks("edited")
        viewModel.changeSettings { it.copy(carrier = "h3", fragment = false) }
        viewModel.save()
        withTimeout(3_000) { viewModel.state.first { it.savedGuid != null } }
        assertEquals("server-guid", receivedGuid)
        assertEquals("group-guid", receivedProfile?.subscriptionId)
        assertEquals(original.addedTime, receivedProfile?.addedTime)
        assertEquals("edited", receivedProfile?.remarks)
        assertEquals("h3", receivedProfile?.aetherSettings?.carrier)
        assertEquals(false, receivedProfile?.aetherSettings?.fragment)
    }

    @Test
    fun emptyNameAndInvalidSettingsNeverWriteProfiles() = runBlocking {
        var saves = 0
        val viewModel = AetherEditorViewModel(mock<Application>(), SavedStateHandle(), Dispatchers.Unconfined,
            { _, subscription -> ProfileItem.create(EConfigType.AETHER).copy(subscriptionId = subscription) },
            { _, _ -> saves++; "new-guid" })
        viewModel.initialize("", "group-guid")
        withTimeout(3_000) { viewModel.state.first { !it.loading } }
        viewModel.save()
        assertEquals(R.string.aether_invalid_settings, viewModel.state.value.errorRes)
        viewModel.changeRemarks("new")
        viewModel.changeSettings { it.copy(socksPort = "bad") }
        viewModel.save()
        assertEquals(R.string.aether_invalid_settings, viewModel.state.value.errorRes)
        assertEquals(0, saves)
        assertNull(viewModel.state.value.savedGuid)
    }

    @Test
    fun missingProfileProducesExplicitLoadError() = runBlocking {
        val viewModel = AetherEditorViewModel(mock<Application>(), SavedStateHandle(), Dispatchers.Unconfined,
            { _, _ -> null }, { _, _ -> error("Must not save a missing profile") })
        viewModel.initialize("missing-guid", "group-guid")
        withTimeout(3_000) { viewModel.state.first { !it.loading } }
        assertEquals(R.string.aether_load_error, viewModel.state.value.errorRes)
        viewModel.save()
        assertNull(viewModel.state.value.savedGuid)
    }

    @Test
    fun draftRestorationUsesSavedValuesInsteadOfTheStoredProfile() = runBlocking {
        val settings = AetherSettings(carrier = "h3", noise = "off", fragment = false)
        val savedState = SavedStateHandle(mapOf("aether_remarks" to "draft", "aether_draft" to Gson().toJson(settings)))
        val viewModel = AetherEditorViewModel(mock<Application>(), savedState, Dispatchers.Unconfined,
            { _, _ -> ProfileItem.create(EConfigType.AETHER).copy(remarks = "stored") },
            { _, _ -> "server-guid" })
        viewModel.initialize("server-guid", "group-guid")
        withTimeout(3_000) { viewModel.state.first { !it.loading } }
        assertEquals("draft", viewModel.state.value.remarks)
        assertEquals(settings, viewModel.state.value.settings)
    }
}

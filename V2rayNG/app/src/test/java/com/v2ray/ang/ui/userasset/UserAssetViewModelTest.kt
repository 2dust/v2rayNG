package com.v2ray.ang.ui.userasset

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class UserAssetViewModelTest {
    private val viewModel = UserAssetViewModel(mock(Application::class.java))

    @Test
    fun builtInIdentitySurvivesReloadAndSourceChange() {
        val first = viewModel.buildAssetList(null, "first/source")
        val reloaded = viewModel.buildAssetList(emptyList(), "second/source")

        assertEquals(3, first.size)
        assertEquals(first.size, first.map { it.guid }.distinct().size)
        assertEquals(first.map { it.guid }, reloaded.map { it.guid })
        assertTrue(reloaded.all { it.assetUrl.locked == true })
        assertEquals(
            AppConfig.GEOIP_ONLY_CN_PRIVATE_URL,
            reloaded.single { it.assetUrl.remarks == AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT }.assetUrl.url,
        )
    }

    @Test
    fun savedAssetKeepsItsIdentityAndReplacesMatchingBuiltIn() {
        val saved = AssetUrlCache("saved-guid", AssetUrlItem(AppConfig.GEOSITE_DAT, "https://example.invalid/geosite.dat"))
        val custom = AssetUrlCache("custom-guid", AssetUrlItem("custom.dat", "file"))
        val builtIns = viewModel.buildAssetList(emptyList(), "source")
        val rows = viewModel.buildAssetList(listOf(saved, custom), "source")

        assertEquals(4, rows.size)
        assertEquals(saved, rows.single { it.assetUrl.remarks == saved.assetUrl.remarks })
        assertEquals(custom, rows.single { it.guid == custom.guid })
        assertEquals(
            builtIns.filter { it.assetUrl.remarks != AppConfig.GEOSITE_DAT }.map { it.guid },
            rows.filter { it.assetUrl.locked == true }.map { it.guid },
        )
    }
}

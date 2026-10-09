package com.v2ray.ang.ui.xfinder

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.v2ray.ang.ui.base.BaseComponentActivity

/** The three tabs reachable from the main drawer. */
enum class XfinderPage { CONFIGS, SCANNER, LOGS }

/**
 * One host for the Xfinder configs, IP scanner and log pages. Each page's ViewModel is created
 * lazily, so opening the log page never triggers the scanner or config downloads.
 */
class XfinderActivity : BaseComponentActivity() {
    private val xfinderViewModel: XfinderViewModel by viewModels()
    private val scannerViewModel: ScannerViewModel by viewModels()
    private val logViewModel: XfinderLogViewModel by viewModels()

    private val page: XfinderPage
        get() = intent.getStringExtra(EXTRA_PAGE)
            ?.let { name -> XfinderPage.entries.firstOrNull { it.name == name } }
            ?: XfinderPage.CONFIGS

    @Composable
    override fun ScreenContent() {
        when (page) {
            XfinderPage.CONFIGS -> XfinderConfigsScreen(onBack = ::finish, viewModel = xfinderViewModel)
            XfinderPage.SCANNER -> ScannerScreen(onBack = ::finish, viewModel = scannerViewModel)
            XfinderPage.LOGS -> XfinderLogScreen(onBack = ::finish, viewModel = logViewModel)
        }
    }

    companion object {
        private const val EXTRA_PAGE = "xfinder_page"

        fun intent(context: Context, page: XfinderPage): Intent =
            Intent(context, XfinderActivity::class.java).putExtra(EXTRA_PAGE, page.name)
    }
}

package com.v2ray.ang.ui.apppicker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppSelectionMenuAction
import com.v2ray.ang.ui.compose.AppSelectionTopBar
import com.v2ray.ang.ui.compose.dpadMovePreviousNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.verticalScrollbar

class AppPickerActivity : BaseComponentActivity() {

    companion object {
        private const val EXTRA_SELECTED_PACKAGES = "selected_packages"
        private const val EXTRA_PICKER_TITLE = "picker_title"

        fun createIntent(
            context: Context,
            selectedPackages: Collection<String> = emptyList(),
            title: String? = null
        ): Intent = Intent(context, AppPickerActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(selectedPackages))
            title?.let { putExtra(EXTRA_PICKER_TITLE, it) }
        }

        fun getSelectedPackages(intent: Intent?): List<String> {
            return intent?.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        }
    }

    private val viewModel: AppPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        viewModel.initialize(initial)
        viewModel.loadApps(this)
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(viewModel.getSelectedPackages()))
            }
        )
        super.finish()
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()

        AppPickerScreen(
            title = resolveScreenTitle(),
            apps = apps,
            isLoading = isLoading,
            selectedPackages = selectedPackages,
            onBackClick = { finish() },
            onToggleApp = { viewModel.toggleApp(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() }
        )
    }

    private fun resolveScreenTitle(): String {
        return intent.getStringExtra(EXTRA_PICKER_TITLE) ?: getString(R.string.per_app_proxy_settings)
    }
}

@Composable
fun AppPickerScreen(
    title: String,
    apps: List<AppInfo>,
    isLoading: Boolean,
    selectedPackages: Set<String>,
    onBackClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val backFocusRequester = rememberDpadFocusRequester(requestFocus = !showSearch, requestKey = showSearch)
    val packageNames = apps.map { it.packageName }
    val rowFocusRequesters = rememberDpadFocusTargets(packageNames) { FocusRequester() }
    val requestRowFocus = rememberLazyDpadFocus(packageNames.map { listOf(rowFocusRequesters.getValue(it)) }) {
        listState.scrollToItem(it)
    }
    val focusFirstApp = {
        apps.firstOrNull()?.let { rowFocusRequesters[it.packageName]?.let(requestRowFocus) } ?: false
    }
    LaunchedEffect(searchQuery) {
        onSearch(searchQuery)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppSelectionTopBar(
                title = title,
                isLoading = isLoading,
                isSearchActive = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchClose = {
                    searchQuery = ""
                    onSearch("")
                    showSearch = false
                },
                onSearchOpen = { showSearch = true },
                onBackClick = onBackClick,
                backFocusRequester = backFocusRequester,
                onMoveDown = focusFirstApp,
                menuActions = listOf(
                    AppSelectionMenuAction(stringResource(R.string.menu_item_select_all), onSelectAll),
                    AppSelectionMenuAction(stringResource(R.string.menu_item_invert_selection), onInvertSelection)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvSafeAreaPadding()
                .dpadMovePreviousNavigation { backFocusRequester.requestFocus() }
                .verticalScrollbar(listState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            itemsIndexed(items = apps, key = { _, app -> app.packageName }) { index, app ->
                val checked = selectedPackages.contains(app.packageName)
                val focusRequester = rowFocusRequesters.getValue(app.packageName)
                AppListItem(
                    appName = app.appName,
                    packageName = app.packageName,
                    icon = null,
                    checked = checked,
                    onCheckedChange = { onToggleApp(app.packageName) },
                    focusRequester = focusRequester,
                    modifier = Modifier.dpadVerticalFocusNavigation(
                        onMoveUp = {
                            apps.getOrNull(index - 1)?.let {
                                rowFocusRequesters[it.packageName]?.let(requestRowFocus)
                            } ?: false
                        },
                        onMoveDown = {
                            apps.getOrNull(index + 1)?.let {
                                rowFocusRequesters[it.packageName]?.let(requestRowFocus)
                            } ?: true
                        }
                    )
                )
                ItemDivider()
            }
        }
    }
}

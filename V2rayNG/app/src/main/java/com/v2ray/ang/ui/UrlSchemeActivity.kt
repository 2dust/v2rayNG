package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.ui.mobile.base.BaseComponentActivity
import com.v2ray.ang.ui.mobile.main.MainActivity
import com.v2ray.ang.vm.UrlSchemeViewModel
import kotlinx.coroutines.launch

class UrlSchemeActivity : BaseComponentActivity() {

    private val viewModel: UrlSchemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeViewModel()
        viewModel.importFromIntent(intent)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewModelEvent.collect { event ->
                    when (event) {
                        is UrlSchemeViewModel.UrlSchemeViewModelEvent.ImportFinished -> {
                            startActivity(Intent(this@UrlSchemeActivity, MainActivity::class.java).apply {
                                if (event.imported) putExtra(AppConfig.EXTRA_REFRESH_AFTER_IMPORT, true)
                            })
                            finish()
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
    }
}

package com.v2ray.ang.ui.urlscheme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.v2ray.ang.ui.base.BaseScreen

@Composable
fun UrlSchemeScreen(viewModel: UrlSchemeViewModel) {
    BaseScreen(viewModel = viewModel) { _, _ ->
        Box(Modifier.fillMaxSize())
    }
}

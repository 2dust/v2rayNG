package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp

@Composable
internal fun TvEditorForm(
    innerPadding: PaddingValues, back: FocusRequester, focus: EditorFocus,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp), content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val ime = rememberTvEditorIme()
    CompositionLocalProvider(LocalTvEditor provides back, LocalEditorFocus provides focus) {
        Column(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).then(ime.padding)
            .padding(horizontal = 48.dp).verticalScroll(scroll).verticalScrollbar(scroll).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
            NavigationBarsSpacer()
        }
    }
}

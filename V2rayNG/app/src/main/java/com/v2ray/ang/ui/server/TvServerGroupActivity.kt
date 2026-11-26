package com.v2ray.ang.ui.server

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager.LayoutParams
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.v2ray.ang.enums.EConfigType

class TvServerGroupActivity : BaseServerGroupActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) hideRestoredKeyboard()
    }

    @Composable
    override fun EditorLayout(onBack: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, fields: @Composable ColumnScope.() -> Unit) =
        TvServerEditor(EConfigType.POLICYGROUP.toString(), onBack, onSave, onDelete, content = fields)

    @Composable
    override fun FallbackField(value: String, onSelect: (String) -> Unit) = TvPolicyFallbackField(value, onSelect)
}

internal fun Activity.hideRestoredKeyboard() {
    val adjustment = window.attributes.softInputMode and LayoutParams.SOFT_INPUT_MASK_ADJUST
    window.setSoftInputMode(adjustment or LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
}

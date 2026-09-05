package com.v2ray.ang.ui.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.v2ray.ang.R
import com.v2ray.ang.repository.TranslatorGroup
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseUiState

/**
 * The single state of the about screen.
 */
@Immutable
data class AboutUiState(
    val versionText: String = "",
    val appId: String = "",
    /** `true` while the translators sub-page replaces the menu. */
    val showTranslators: Boolean = false,
    val translators: List<TranslatorGroup> = emptyList()
) : BaseUiState

/**
 * The about menu, declared once in display order.
 */
enum class AboutEntry(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int
) {
    SOURCE_CODE(R.drawable.ic_source_code_24dp, R.string.title_source_code),
    OSS_LICENSE(R.drawable.license_24px, R.string.title_oss_license),
    TRANSLATORS(R.drawable.ic_translate_24dp, R.string.title_translators),
    FEEDBACK(R.drawable.ic_feedback_24dp, R.string.title_pref_feedback),
    TG_CHANNEL(R.drawable.ic_telegram_24dp, R.string.title_tg_channel),
    PRIVACY_POLICY(R.drawable.ic_privacy_24dp, R.string.title_privacy_policy)
}

/** The complete set of user intents of the about screen. */
sealed interface AboutAction : BaseAction {

    /** Back press or back button; the ViewModel decides "leave sub-page" vs "close screen". */
    data object Back : AboutAction

    /** One menu row was tapped; the ViewModel decides link vs. dialog vs. sub-page. */
    data class EntryClicked(val entry: AboutEntry) : AboutAction

    /** A contributor profile link was tapped. */
    data class LinkClicked(val url: String) : AboutAction
}

/** One-time effects of this screen, intercepted inside `BaseScreen(onEvent = …)`. */
sealed interface AboutEvent : BaseEvent.Platform {

    /** The licenses are rendered by a WebView the UI owns, so the ViewModel only asks for it. */
    data object ShowOssLicense : AboutEvent
}

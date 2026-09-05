package com.v2ray.ang.repository

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.TranslatorsParser
import com.v2ray.ang.util.Utils

/**
 * One contributor line, already resolved for display.
 */
data class TranslatorRow(
    val id: String,
    val displayName: String,
    val url: String?
)

/** One language block of the translators page. */
data class TranslatorGroup(
    val language: String,
    val members: List<TranslatorRow>
)

/**
 * Data layer of the about screen: build metadata, the native core version, the static link table
 * and the bundled translators file.
 */
open class AboutRepository(private val app: Application) : BaseRepository() {

    open fun applicationId(): String = BuildConfig.APPLICATION_ID

    open fun shortVersionText(): String = "$VERSION_PREFIX${BuildConfig.VERSION_NAME}"

    open suspend fun fullVersionText(): String = withIO {
        "$VERSION_PREFIX${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})"
    }

    open fun sourceCodeUrl(): String = AppConfig.APP_URL

    open fun issuesUrl(): String = AppConfig.APP_ISSUES_URL

    open fun tgChannelUrl(): String = AppConfig.TG_CHANNEL_URL

    open fun privacyPolicyUrl(): String = AppConfig.APP_PRIVACY_POLICY

    /**
     * Reads and projects `translators.json`.
     */
    open suspend fun loadTranslators(): List<TranslatorGroup> = runIO(emptyList()) {
        TranslatorsParser.parse(Utils.readTextFromAssets(app, TRANSLATORS_ASSET))
            .map { credit ->
                TranslatorGroup(
                    language = credit.language,
                    members = credit.contributors.mapIndexed { index, contributor ->
                        TranslatorRow(
                            id = "${credit.language}#$index",
                            displayName = contributor.displayName
                                ?.takeIf { it.isNotBlank() }
                                ?: contributor.name,
                            url = contributor.url?.takeIf { it.isNotBlank() }
                        )
                    }
                )
            }
            .filter { it.members.isNotEmpty() }
    }

    private companion object {
        const val VERSION_PREFIX = "v"
        const val TRANSLATORS_ASSET = "translators.json"
    }
}

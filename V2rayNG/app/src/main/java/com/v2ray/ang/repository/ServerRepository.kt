package com.v2ray.ang.repository

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionOption
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager

open class ServerRepository(private val app: Application) : BaseRepository() {

    open suspend fun loadProfile(guid: String, fallbackType: EConfigType): ProfileItem = withIO {
        MmkvManager.decodeServerConfig(guid) ?: ProfileItem.create(fallbackType)
    }

    open suspend fun loadRawConfig(guid: String): String = withIO {
        MmkvManager.decodeServerRaw(guid).orEmpty()
    }

    open suspend fun saveProfile(guid: String, profile: ProfileItem): String = withIO {
        MmkvManager.encodeServerConfig(guid, profile)
    }

    open suspend fun saveRawConfig(guid: String, content: String) = withIO {
        MmkvManager.encodeServerRaw(guid, content)
    }

    open suspend fun removeProfile(guid: String) = withIO {
        MmkvManager.removeServer(guid)
    }

    open suspend fun isSelectedServer(guid: String): Boolean = withIO {
        guid.isNotEmpty() && guid == MmkvManager.getSelectServer()
    }

    open suspend fun generateDescription(profile: ProfileItem): String = withIO {
        AngConfigManager.generateDescription(profile)
    }

    open suspend fun parseCustomConfig(content: String): ProfileItem? = withIO {
        CustomFmt.parse(content)
    }

    open suspend fun fetchCertSha256(profile: ProfileItem): String? = withIO {
        CertificateFingerprintManager.fetchForManualFill(profile)
    }

    open suspend fun findProfileByRemarks(remarks: String): ProfileItem? = withIO {
        SettingsManager.getServerViaRemarks(remarks)
    }

    open suspend fun loadSubscriptions(): List<SubscriptionOption> = withIO {
        buildList {
            add(SubscriptionOption(id = "", name = ""))
            MmkvManager.decodeSubscriptions().forEach { cache ->
                add(
                    SubscriptionOption(
                        id = cache.guid,
                        name = cache.subscription.remarks.ifBlank { cache.guid },
                    )
                )
            }
        }
    }

    open suspend fun loadChainCandidates(): List<String> = withIO {
        SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(
                EConfigType.CUSTOM,
                EConfigType.POLICYGROUP,
                EConfigType.PROXYCHAIN,
            )
        )
    }

    open suspend fun loadFallbackTags(): List<String> = withIO {
        (AppConfig.BUILTIN_OUTBOUND_TAGS + SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP)
        )).filter { it != AppConfig.TAG_PROXY }
    }

    open suspend fun buildPolicyGroupDescription(
        typeIndex: Int,
        subId: String,
        filter: String,
    ): String = withIO {
        val typeName = app.resources.getStringArray(R.array.policy_group_type)
            .getOrNull(typeIndex).orEmpty()
        val subName = if (subId.isEmpty()) {
            app.getString(R.string.filter_config_all)
        } else {
            MmkvManager.decodeSubscription(subId)?.remarks?.ifBlank { subId } ?: subId
        }
        "$typeName - $subName - $filter"
    }
}

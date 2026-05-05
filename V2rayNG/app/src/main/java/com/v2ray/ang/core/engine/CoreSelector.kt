package com.v2ray.ang.core.engine

import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.enums.EConfigType

object CoreSelector {
    const val SING_BOX_TEST_PREFIX = "[sing-box]"

    /**
     * Keeps the current runtime behavior unchanged while introducing a single
     * place for future per-profile or per-subscription core selection.
     *
     * Future work can inspect protocol families, subscription metadata, or
     * resolved feature requirements and route eligible profiles to sing-box.
     */
    fun resolve(profile: ProfileItem): CoreType {
        if (isExplicitSingBoxTestProfile(profile)) {
            return CoreType.SING_BOX
        }
        return CoreType.XRAY
    }

    fun isExplicitSingBoxTestProfile(profile: ProfileItem): Boolean {
        return profile.configType == EConfigType.CUSTOM &&
                profile.remarks.trimStart().startsWith(SING_BOX_TEST_PREFIX, ignoreCase = true)
    }
}

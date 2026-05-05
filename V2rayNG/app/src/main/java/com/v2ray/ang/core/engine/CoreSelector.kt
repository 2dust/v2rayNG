package com.v2ray.ang.core.engine

import com.v2ray.ang.dto.ProfileItem

object CoreSelector {
    /**
     * Keeps the current runtime behavior unchanged while introducing a single
     * place for future per-profile or per-subscription core selection.
     *
     * Future work can inspect protocol families, subscription metadata, or
     * resolved feature requirements and route eligible profiles to sing-box.
     */
    fun resolve(profile: ProfileItem): CoreType {
        return CoreType.XRAY
    }
}

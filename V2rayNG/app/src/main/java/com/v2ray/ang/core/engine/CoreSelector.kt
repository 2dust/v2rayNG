package com.v2ray.ang.core.engine

import com.v2ray.ang.dto.ProfileItem

object CoreSelector {
    /**
     * Keeps the current runtime behavior unchanged while introducing a single
     * place for future per-profile or per-subscription core selection.
     */
    fun resolve(profile: ProfileItem): CoreType {
        return CoreType.XRAY
    }
}

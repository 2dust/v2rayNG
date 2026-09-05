package com.v2ray.ang.ui.server

import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.util.JsonUtil

/**
 * Centralised validation rules for the server configuration screen.
 * This object is Context‑free, side‑effect‑free, and unit‑testable.
 * Each validation function returns `null` on success, or a [BaseText] containing the
 * user‑friendly error message to display.
 */
internal object ServerValidator {

    /**
     * Validates the basic form fields that are common to all protocol types.
     * This corresponds to the legacy [validateBasicConfig] logic.
     *
     * @param configType The selected protocol type (affects whether port is required).
     * @param form The [ServerForm] snapshot to validate.
     * @return `null` if valid, otherwise an error [BaseText].
     */
    fun validateForm(configType: EConfigType, form: ServerForm): BaseText? {
        if (form.remarks.isBlank()) return BaseText.of(R.string.server_lab_remarks)
        if (form.address.isBlank()) return BaseText.of(R.string.server_lab_address)
        if (configType != EConfigType.HYSTERIA2 && (form.port.toIntOrNull() ?: 0) <= 0) {
            return BaseText.of(R.string.server_lab_port)
        }
        return null
    }

    /**
     * Validates the protocol‑specific fields of a [ProfileItem].
     * This combines the legacy [validateCommonConfig] and per‑protocol
     * [validateProtocolConfig] checks.
     *
     * @param configType The protocol type of the profile.
     * @param profile The [ProfileItem] to validate (usually derived from the form).
     * @return `null` if all protocol constraints are satisfied, otherwise an error [BaseText].
     */
    fun validateProfile(configType: EConfigType, profile: ProfileItem): BaseText? {
        if (profile.password.isNullOrBlank()) {
            when (configType) {
                EConfigType.VMESS, EConfigType.VLESS ->
                    return BaseText.of(R.string.server_lab_id)

                EConfigType.TROJAN, EConfigType.SHADOWSOCKS, EConfigType.HYSTERIA2 ->
                    return BaseText.of(R.string.server_lab_id3)

                else -> Unit
            }
        }
        if (configType == EConfigType.TROJAN && profile.security.isNullOrBlank()) {
            return BaseText.of(R.string.server_lab_stream_security)
        }
        if (!profile.xhttpExtra.isNullOrBlank() && JsonUtil.parseString(profile.xhttpExtra) == null) {
            return BaseText.of(R.string.server_lab_xhttp_extra)
        }
        if (!profile.finalMask.isNullOrBlank() && JsonUtil.parseString(profile.finalMask) == null) {
            return BaseText.of(R.string.server_lab_final_mask)
        }
        return null
    }
}

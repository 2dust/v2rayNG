package com.v2ray.ang.ui.server

import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class XdriveValidationTest {

    private fun profile(
        service: String? = AppConfig.XDRIVE_SERVICE_GOOGLE_DRIVE,
        secrets: String? = "id,secret,token",
        extra: String? = null,
        encryption: String? = "mlkem768x25519plus.native.0rtt.key",
        flow: String? = "",
        folder: String? = "folder-id",
        network: String = NetworkType.XDRIVE.type
    ) = ProfileItem.create(EConfigType.VLESS).apply {
        this.network = network
        this.path = folder
        this.method = encryption
        this.flow = flow
        xdriveService = service
        xdriveSecrets = secrets
        xdriveExtra = extra
    }

    @Test
    fun nonXdriveProfileIsIgnored() {
        assertNull(xdriveValidationError(profile(network = NetworkType.TCP.type, encryption = "none")))
    }

    @Test
    fun validGoogleDriveProfilePasses() {
        assertNull(xdriveValidationError(profile()))
    }

    @Test
    fun validYandexDiskProfilePasses() {
        assertNull(xdriveValidationError(profile(AppConfig.XDRIVE_SERVICE_YANDEX_DISK, "login,password")))
    }

    @Test
    fun validTemplateProfilePasses() {
        assertNull(
            xdriveValidationError(
                profile(AppConfig.XDRIVE_SERVICE_TEMPLATE, secrets = null, extra = """{"template":{"flatten":true}}""")
            )
        )
    }

    @Test
    fun encryptionIsRequired() {
        assertEquals(R.string.toast_xdrive_encryption, xdriveValidationError(profile(encryption = "none")))
        assertEquals(R.string.toast_xdrive_encryption, xdriveValidationError(profile(encryption = " ")))
        assertEquals(R.string.toast_xdrive_encryption, xdriveValidationError(profile(encryption = null)))
    }

    @Test
    fun flowIsRejected() {
        assertEquals(R.string.toast_xdrive_flow, xdriveValidationError(profile(flow = "xtls-rprx-vision")))
    }

    @Test
    fun folderIsRequired() {
        assertEquals(R.string.server_lab_xdrive_folder, xdriveValidationError(profile(folder = "")))
        assertEquals(R.string.server_lab_xdrive_folder, xdriveValidationError(profile(folder = null)))
    }

    @Test
    fun invalidExtraJsonIsRejected() {
        assertEquals(R.string.server_lab_xdrive_extra, xdriveValidationError(profile(extra = "{broken")))
        assertEquals(R.string.server_lab_xdrive_extra, xdriveValidationError(profile(extra = "[1,2]")))
    }

    @Test
    fun secretCountIsCheckedPerService() {
        assertEquals(R.string.toast_xdrive_secrets, xdriveValidationError(profile(secrets = "id,secret")))
        assertEquals(R.string.toast_xdrive_secrets, xdriveValidationError(profile(secrets = null)))
        assertEquals(
            R.string.toast_xdrive_secrets,
            xdriveValidationError(profile(AppConfig.XDRIVE_SERVICE_YANDEX_DISK, "login"))
        )
    }

    @Test
    fun templateServiceNeedsTemplateObject() {
        assertEquals(
            R.string.toast_xdrive_template,
            xdriveValidationError(profile(AppConfig.XDRIVE_SERVICE_TEMPLATE, extra = null))
        )
        assertEquals(
            R.string.toast_xdrive_template,
            xdriveValidationError(profile(AppConfig.XDRIVE_SERVICE_TEMPLATE, extra = """{"template":"x"}"""))
        )
    }

    @Test
    fun unknownServiceIsRejected() {
        assertEquals(R.string.server_lab_xdrive_service, xdriveValidationError(profile(service = "dropbox")))
        assertEquals(R.string.server_lab_xdrive_service, xdriveValidationError(profile(service = null)))
    }
}

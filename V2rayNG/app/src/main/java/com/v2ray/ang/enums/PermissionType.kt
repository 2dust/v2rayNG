package com.v2ray.ang.enums

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import com.v2ray.ang.R

/**
 * Permission types used in the app, handling API level differences.
 */
enum class PermissionType {
    /** Camera permission (used for scanning QR codes) */
    CAMERA {
        override fun getPermission(): String = Manifest.permission.CAMERA
    },

    /** Notification permission (Android 13+) */
    POST_NOTIFICATIONS {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun getPermission(): String = Manifest.permission.POST_NOTIFICATIONS
    },

    /** Local network access permission (Android 17+) */
    ACCESS_LOCAL_NETWORK {
        @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
        override fun getPermission(): String = Manifest.permission.ACCESS_LOCAL_NETWORK
    };

    /** Return the actual Android permission string */
    abstract fun getPermission(): String

    /** Return the string resource for the human-readable permission label. */
    @StringRes
    fun getLabelRes(): Int {
        return when (this) {
            CAMERA -> R.string.permission_camera
            POST_NOTIFICATIONS -> R.string.permission_notification
            ACCESS_LOCAL_NETWORK -> R.string.permission_local_network
        }
    }
}

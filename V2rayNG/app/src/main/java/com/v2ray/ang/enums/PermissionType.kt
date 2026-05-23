package com.v2ray.ang.enums

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

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
    };

    /** Return the actual Android permission string */
    abstract fun getPermission(): String

    /** Return a human-readable label for the permission */
    fun getLabel(): String {
        return when (this) {
            CAMERA -> "Camera"
            POST_NOTIFICATIONS -> "Notification"
        }
    }
}
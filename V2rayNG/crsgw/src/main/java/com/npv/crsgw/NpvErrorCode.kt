package com.npv.crsgw

import android.content.Context
import androidx.annotation.StringRes

enum class NpvErrorCode(val code: Int, @StringRes val messageResId: Int) {
    UNKNOWN_ERROR(1001, R.string.npv_error_unknown),
    NETWORK_ERROR(1002, R.string.npv_error_network),
    SERVER_ERROR(1003, R.string.npv_error_server);

    companion object {
        fun fromCode(code: Int): NpvErrorCode? {
            return entries.find { it.code == code }
        }

        fun getMessage(context: Context, code: Int): String {
            val error = fromCode(code)
            return if (error != null) {
                context.getString(error.messageResId)
            } else {
                context.getString(R.string.npv_error_unknown)
            }
        }
    }
}
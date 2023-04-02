package com.v2ray.ang.util

import android.app.Activity
import androidx.fragment.app.Fragment

object CallbackUtil {

    fun <T> getCallback(fragment: Fragment, callback: Class<T>): T? {
        if (callback.isInstance(fragment.targetFragment)) {

            return fragment.targetFragment as T
        }
        if (fragment.parentFragment != null) {
            if (callback.isInstance(fragment.parentFragment)) {

                return fragment.parentFragment as T
            } else if (callback.isInstance(fragment.parentFragment?.parentFragment)) {

                return fragment.parentFragment?.parentFragment as T
            }
        }
        return if (callback.isInstance(fragment.activity)) {

            fragment.activity as T
        } else null
    }

    fun <T> getCallback(activity: Activity, callback: Class<T>): T? {
        return if (callback.isInstance(activity)) {
            activity as T
        } else null
    }

}

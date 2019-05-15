package com.v2ray.ang.extension

import android.preference.Preference

fun Preference.onClick(listener: () -> Unit) {
    setOnPreferenceClickListener {
        listener()
        true
    }
}
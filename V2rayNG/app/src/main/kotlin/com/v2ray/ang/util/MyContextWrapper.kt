package com.v2ray.ang.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.*

open class MyContextWrapper(base: Context?) : ContextWrapper(base) {
    companion object {
        @RequiresApi(Build.VERSION_CODES.N)
        fun wrap(context: Context, newLocale: Locale?): ContextWrapper {
            var mContext = context
            val res: Resources = mContext.resources
            val configuration: Configuration = res.configuration
            //注意 Android 7.0 前后的不同处理方法
            mContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocale(newLocale)
                val localeList = LocaleList(newLocale)
                LocaleList.setDefault(localeList)
                configuration.setLocales(localeList)
                mContext.createConfigurationContext(configuration)
            } else {
                configuration.setLocale(newLocale)
                mContext.createConfigurationContext(configuration)
            }
            return ContextWrapper(mContext)
        }
    }
}
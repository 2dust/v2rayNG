package com.v2ray.ang.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

open class MyContextWrapper(base: Context?) : ContextWrapper(base) {
    companion object {
        /**
         * Wraps the context with a new locale.
         *
         * @param context The original context.
         * @param newLocale The new locale to set.
         * @return A ContextWrapper with the new locale.
         */
        @RequiresApi(Build.VERSION_CODES.N)
        fun wrap(context: Context, newLocale: Locale?): ContextWrapper {
            var mContext = context
            val res: Resources = mContext.resources
            val configuration: Configuration = res.configuration
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
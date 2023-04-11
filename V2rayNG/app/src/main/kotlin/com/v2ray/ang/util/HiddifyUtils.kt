package com.v2ray.ang.util

import android.content.Context
import android.text.SpannableString
import com.v2ray.ang.R
import com.v2ray.ang.extension.*
import com.v2ray.ang.util.Utils.getLocale
import java.util.Locale

object HiddifyUtils {
    fun toTotalUsedGig(totalInBytes: Long, usedInBytes: Long, context: Context): String {
        val total = totalInBytes.toDouble() / 1073741824
        val used = usedInBytes.toDouble() / 1073741824
        return if (getLocale(context) == Locale("fa"))
            String.format("%.0f/%.0f G", used, total).toPersianDigit()
        else
            String.format("%.0f/%.0f G", used, total)
    }

    fun timeToRelativeDate(time: Long, totalInBytes: Long, usedInBytes: Long, context: Context): SpannableString {
        if (time < 0)
            return "".bold("")

        val now = System.currentTimeMillis() / 1000
        val diffInMillis = (time - now) / 86400
        if (diffInMillis <= 0)
            return if (getLocale(context) == Locale("fa") || getLocale(context).toString() == "fa_IR")
                "منقضی شده".bold("")
            else "Expired".bold("")

        if (totalInBytes == usedInBytes)
            return if (getLocale(context) == Locale("fa") || getLocale(context).toString() == "fa_IR")
                "اتمام حجم".bold("")
            else "Completion of the volume".bold("")


        return if (getLocale(context) == Locale("fa") || getLocale(context).toString() == "fa_IR") {
            if (diffInMillis > 10)
                "$diffInMillis روز \n باقیمانده".toPersianDigit()
                    .colorlessTextPart("باقیمانده", context.getColorEx(R.color.colorBorder))
            else
                "$diffInMillis روز \n باقیمانده".toPersianDigit()
                    .colorlessTextPart("باقیمانده", context.getColorEx(R.color.colorBorder))
                    .colorlessTextPart(
                        "${diffInMillis.toString().toPersianDigit()} روز ",
                        context.getColorEx(R.color.colorRed)
                    )
        } else {
            if (diffInMillis > 10)
                "$diffInMillis days \n Remain".colorlessTextPart(
                    "Remain",
                    context.getColorEx(R.color.colorBorder)
                )
            else
                "$diffInMillis days \n Remain".colorlessTextPart(
                    "Remain",
                    context.getColorEx(R.color.colorBorder)
                ).colorlessTextPart("$diffInMillis days ", context.getColorEx(R.color.colorRed))
        }
    }

    fun checkState(time: Long, totalInBytes: Long, usedInBytes: Long): String {
        if (time < 0)
            return "disable"

        val now = System.currentTimeMillis() / 1000
        val diffInMillis = (time - now) / 86400
        if (diffInMillis <= 0)
            return "disable"

        if (totalInBytes == usedInBytes)
            return "disable"

        return "enable"
    }
}


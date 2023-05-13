package com.v2ray.ang.extension

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.util.Utils
import java.util.*


fun String.toPersianDigit(context:Context?=AngApplication.appContext): String {
    if(context!=null &&!Utils.getLocale(context).toString().contains("fa"))
        return this

    var number = this
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    val english = "0123456789"

    for (i in 0..9) {
        val p = "" + persian[i]
        val e = "" + english[i]
        number = number.replace(e.toRegex(), p)
    }

    return number
}

fun View?.show() {
    this?.visibility = View.VISIBLE
}

fun View?.gone() {
    this?.visibility = View.GONE
}

fun View?.hide() {
    this?.visibility = View.INVISIBLE
}

fun View?.showGone(show:Boolean) {
    if (show) {
        this?.show()
    }else{
        this?.gone()
    }
}

fun View?.showHide(show:Boolean) {
    if (show) {
        this?.show()
    }else{
        this?.hide()
    }
}

class SpannableBuilder {
    lateinit var what: Any
    var flags: Int = 0
}

fun SpannableString.spanWith(target: String, apply: SpannableBuilder.() -> Unit) {
    val builder = SpannableBuilder()
    apply(builder)

    var index = this.indexOf(target)
    while (index >= 0) {
        val start = index
        val end =  start + target.length

        setSpan(builder.what, start, end, builder.flags)
        index = this.indexOf(target, index + 1)
    }

}

fun SpannableString.bold(target: String): SpannableString {

    this.spanWith(target){
        what = StyleSpan(Typeface.BOLD)
        flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    }

    return this
}

fun String.bold(query: String): SpannableString {
    val mySpannedText = SpannableString(this)

    if (!contains(query)) {
        return mySpannedText
    }
    return mySpannedText.bold(query)
}

fun String.colorlessTextPart(
    textToHighlight: String,
    color: Int,
    highlightAll: Boolean = true
): SpannableString {
    val spannableString = SpannableString(this)
    if (textToHighlight.isEmpty()) {
        return spannableString
    }

    var startIndex = this.indexOf(textToHighlight, 0, true)
    val indexes = ArrayList<Int>()
    while (startIndex >= 0) {
        if (startIndex != -1) {
            indexes.add(startIndex)
        }

        startIndex = this.indexOf(textToHighlight, startIndex + textToHighlight.length, true)
        if (!highlightAll) {
            break
        }
    }

    indexes.forEach {
        val endIndex = kotlin.math.min(it + textToHighlight.length, length)
        try {
            spannableString.setSpan(
                ForegroundColorSpan(color),
                it,
                endIndex,
                Spannable.SPAN_EXCLUSIVE_INCLUSIVE
            )
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    return spannableString
}

fun SpannableString.colorlessTextPart(
    textToHighlight: String,
    color: Int,
    highlightAll: Boolean = true
): SpannableString {
    val spannableString = this
    if (textToHighlight.isEmpty()) {
        return spannableString
    }

    var startIndex = this.indexOf(textToHighlight, 0, true)
    val indexes = ArrayList<Int>()
    while (startIndex >= 0) {
        if (startIndex != -1) {
            indexes.add(startIndex)
        }

        startIndex = this.indexOf(textToHighlight, startIndex + textToHighlight.length, true)
        if (!highlightAll) {
            break
        }
    }

    indexes.forEach {
        val endIndex = kotlin.math.min(it + textToHighlight.length, length)
        try {
            spannableString.setSpan(
                ForegroundColorSpan(color),
                it,
                endIndex,
                Spannable.SPAN_EXCLUSIVE_INCLUSIVE
            )
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    return spannableString
}

fun Context?.toast(s: String?, length : Int = Toast.LENGTH_SHORT) {
    if(this==null)
        return
    if (this is Activity) {
        if ((this.isFinishing || this.isDestroyed)) {
            return
        }
    }

    if (s == null || s.isBlank()) {
        return
    }

    val inflater = LayoutInflater.from(this)
    val layout = inflater.inflate(R.layout.custom_toast, null)
    val text = layout.findViewById<TextView>(R.id.text)
    text.text = s

    val toast = Toast(this)
    toast.setGravity(Gravity.BOTTOM, 0, 150)
    toast.duration = length
    toast.view = layout
    toast.show()
}

fun Context?.toast(i: Int?, length : Int = Toast.LENGTH_LONG) {

    if (this is Activity) {
        if ((this.isFinishing || this.isDestroyed)) {
            return
        }
    }

    i?.let {
        val s = this?.getString(it)

        if (s == null || s.isBlank()) {
            return
        }

        val inflater = LayoutInflater.from(this)
        val layout = inflater.inflate(R.layout.custom_toast, null)
        val text = layout.findViewById<TextView>(R.id.text)
        text.text = s

        val toast = Toast(this)
        toast.setGravity(Gravity.BOTTOM, 0, 150)
        toast.duration = length
        toast.view = layout
        toast.show()
    }
}

fun Context?.getColorEx(color:Int):Int{
    return ContextCompat.getColor(this?:return 0, color)
}

/**
 * Set an onclick listener
 */
fun <T : View> T?.click(block: (T) -> Unit) = this?.setOnClickListener { block(it as T) }
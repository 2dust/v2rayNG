package com.v2ray.ang.extension

import android.app.Fragment
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.database.Cursor
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AlertDialog
import android.view.KeyEvent
import android.view.View
import android.widget.ListAdapter


fun Context.alertView(
        title: String? = null,
        view: View,
        init: (KAlertDialogBuilder.() -> Unit)? = null
) = KAlertDialogBuilder(this).apply {
    if (title != null) title(title)
    if (title != null) customView(view)
    if (init != null) init()
}

fun Fragment.alert(
        message: String,
        title: String? = null,
        init: (KAlertDialogBuilder.() -> Unit)? = null
) = activity.alert(message, title, init)

fun Context.alert(
        message: String,
        title: String? = null,
        init: (KAlertDialogBuilder.() -> Unit)? = null
) = KAlertDialogBuilder(this).apply {
    if (title != null) title(title)
    message(message)
    if (init != null) init()
}

fun Fragment.alert(
        message: Int,
        title: Int? = null,
        init: (KAlertDialogBuilder.() -> Unit)? = null
) = activity.alert(message, title, init)

fun Context.alert(
        message: Int,
        title: Int? = null,
        init: (KAlertDialogBuilder.() -> Unit)? = null
) = KAlertDialogBuilder(this).apply {
    if (title != null) title(title)
    message(message)
    if (init != null) init()
}


fun Fragment.alert(init: KAlertDialogBuilder.() -> Unit): KAlertDialogBuilder = activity.alert(init)

fun Context.alert(init: KAlertDialogBuilder.() -> Unit) = KAlertDialogBuilder(this).apply { init() }

fun Fragment.progressDialog(
        message: Int? = null,
        title: Int? = null,
        init: (ProgressDialog.() -> Unit)? = null
) = activity.progressDialog(message, title, init)

fun Context.progressDialog(
        message: Int? = null,
        title: Int? = null,
        init: (ProgressDialog.() -> Unit)? = null
) = progressDialog(false, message?.let { getString(it) }, title?.let { getString(it) }, init)

fun Fragment.indeterminateProgressDialog(
        message: Int? = null,
        title: Int? = null,
        init: (ProgressDialog.() -> Unit)? = null
) = activity.progressDialog(message, title, init)

fun Context.indeterminateProgressDialog(
        message: Int? = null,
        title: Int? = null,
        init: (ProgressDialog.() -> Unit)? = null
) = progressDialog(true, message?.let { getString(it) }, title?.let { getString(it) }, init)

fun Fragment.progressDialog(
        message: String? = null,
        title: String? = null,
        init: (ProgressDialog.() -> Unit)? = null
) = activity.progressDialog(message, title, init)

fun Context.progressDialog(
        message: String? = null,
        title: String? = null,
        init: (ProgressDialog.() -> Unit)? = null
) = progressDialog(false, message, title, init)

fun Fragment.indeterminateProgressDialog(
        message: String? = null,
        title: String? = null,
        init: (ProgressDialog.() -> Unit)? = null
) = activity.indeterminateProgressDialog(message, title, init)

fun Context.indeterminateProgressDialog(
        message: String? = null,
        title: String? = null,
        init: (ProgressDialog.() -> Unit)? = null
) = progressDialog(true, message, title, init)

private fun Context.progressDialog(
        indeterminate: Boolean,
        message: String? = null,
        title: String? = null,
        init: (ProgressDialog.() -> Unit)? = null
) = ProgressDialog(this).apply {
    isIndeterminate = indeterminate
    if (!indeterminate) setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
    if (message != null) setMessage(message)
    if (title != null) setTitle(title)
    if (init != null) init()
    show()
}

fun Fragment.selector(
        title: CharSequence? = null,
        items: List<CharSequence>,
        onClick: (Int) -> Unit
): Unit = activity.selector(title, items, onClick)

fun Context.selector(
        title: CharSequence? = null,
        items: List<CharSequence>,
        onClick: (Int) -> Unit
) {
    with(KAlertDialogBuilder(this)) {
        if (title != null) title(title)
        items(items, onClick)
        show()
    }
}

class KAlertDialogBuilder(val ctx: Context) {

    val builder: AlertDialog.Builder = AlertDialog.Builder(ctx)
    protected var dialog: AlertDialog? = null

    fun dismiss() {
        dialog?.dismiss()
    }

    fun show(): KAlertDialogBuilder {
        dialog = builder.create()
        dialog!!.show()
        return this
    }

    fun title(title: CharSequence) {
        builder.setTitle(title)
    }

    fun title(resource: Int) {
        builder.setTitle(resource)
    }

    fun message(title: CharSequence) {
        builder.setMessage(title)
    }

    fun message(resource: Int) {
        builder.setMessage(resource)
    }

    fun icon(icon: Int) {
        builder.setIcon(icon)
    }

    fun icon(icon: Drawable) {
        builder.setIcon(icon)
    }

    fun customTitle(title: View) {
        builder.setCustomTitle(title)
    }

    fun customView(view: View) {
        builder.setView(view)
    }

    fun cancellable(value: Boolean = true) {
        builder.setCancelable(value)
    }

    fun onCancel(f: () -> Unit) {
        builder.setOnCancelListener { f() }
    }

    fun onKey(f: (keyCode: Int, e: KeyEvent) -> Boolean) {
        builder.setOnKeyListener({ dialog, keyCode, event -> f(keyCode, event) })
    }

    fun neutralButton(textResource: Int = android.R.string.ok, f: DialogInterface.() -> Unit = { dismiss() }) {
        neutralButton(ctx.getString(textResource), f)
    }

    fun neutralButton(title: String, f: DialogInterface.() -> Unit = { dismiss() }) {
        builder.setNeutralButton(title, { dialog, which -> dialog.f() })
    }

    fun positiveButton(textResource: Int = android.R.string.ok, f: DialogInterface.() -> Unit) {
        positiveButton(ctx.getString(textResource), f)
    }

    fun positiveButton(title: String, f: DialogInterface.() -> Unit) {
        builder.setPositiveButton(title, { dialog, which -> dialog.f() })
    }

    fun negativeButton(textResource: Int = android.R.string.cancel, f: DialogInterface.() -> Unit = { dismiss() }) {
        negativeButton(ctx.getString(textResource), f)
    }

    fun negativeButton(title: String, f: DialogInterface.() -> Unit = { dismiss() }) {
        builder.setNegativeButton(title, { dialog, which -> dialog.f() })
    }

    fun items(itemsId: Int, f: (which: Int) -> Unit) {
        items(ctx.resources!!.getTextArray(itemsId), f)
    }

    fun items(items: List<CharSequence>, f: (which: Int) -> Unit) {
        items(items.toTypedArray(), f)
    }

    fun items(items: Array<CharSequence>, f: (which: Int) -> Unit) {
        builder.setItems(items, { dialog, which -> f(which) })
    }

    fun adapter(adapter: ListAdapter, f: (which: Int) -> Unit) {
        builder.setAdapter(adapter, { dialog, which -> f(which) })
    }

    fun adapter(cursor: Cursor, labelColumn: String, f: (which: Int) -> Unit) {
        builder.setCursor(cursor, { dialog, which -> f(which) }, labelColumn)
    }
}

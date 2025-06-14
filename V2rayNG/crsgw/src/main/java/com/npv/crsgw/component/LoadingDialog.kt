package com.npv.crsgw.component

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import com.npv.crsgw.R
import androidx.core.graphics.drawable.toDrawable

class LoadingDialog(context: Context) : Dialog(context) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
        setContentView(view)
        setCancelable(false)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }
}

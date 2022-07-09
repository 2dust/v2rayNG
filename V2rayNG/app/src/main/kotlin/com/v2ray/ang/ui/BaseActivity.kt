package com.v2ray.ang.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import androidx.annotation.RequiresApi
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.R
import com.v2ray.ang.util.Utils

abstract class BaseActivity : AppCompatActivity() {
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkDarkMode()
    }

    private fun checkDarkMode() {
        if (Utils.getDarkModeStatus(this)) {
            if (this.javaClass.simpleName == "MainActivity") {
                setTheme(R.style.AppThemeDark_NoActionBar)
            } else {
                setTheme(R.style.AppThemeDark)
            }
        } else {
            if (this.javaClass.simpleName == "MainActivity") {
                setTheme(R.style.AppThemeLight_NoActionBar)
            } else {
                setTheme(R.style.AppThemeLight)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase,  Utils.getLocale(newBase))
        }
        super.attachBaseContext(context)
    }



}
package com.v2ray.ang.ui

import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.v2ray.ang.extension.v2RayApplication

abstract class BaseActivity : AppCompatActivity() {
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    fun toast(msgid: Int) {
        v2RayApplication.toast(msgid)
    }
}
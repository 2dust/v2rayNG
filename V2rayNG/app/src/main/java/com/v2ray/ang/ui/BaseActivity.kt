package com.v2ray.ang.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * BaseActivity provides common helpers and UI wiring used across the app's activities.
 */
abstract class BaseActivity : AppCompatActivity() {
    protected val isLoadingFlow = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Utils.getDarkModeStatus(this)) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    protected fun setContentViewWithToolbar(childView: View, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            fitsSystemWindows = true
        }

        val toolbar = MaterialToolbar(this)
        root.addView(toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(showHomeAsUp)
        supportActionBar?.title = title

        val progressBar = LinearProgressIndicator(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(progressBar)

        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        if (childView.parent != null) {
            (childView.parent as ViewGroup).removeView(childView)
        }
        container.addView(childView)
        root.addView(container)

        setContentView(root)

        lifecycleScope.launch {
            isLoadingFlow.collect {
                progressBar.visibility = if (it) View.VISIBLE else View.GONE
            }
        }
    }

    protected fun setContentViewWithToolbar(layoutResId: Int, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val view = LayoutInflater.from(this).inflate(layoutResId, null)
        setContentViewWithToolbar(view, showHomeAsUp, title)
    }

    protected fun showLoading() {
        isLoadingFlow.value = true
    }

    protected fun hideLoading() {
        isLoadingFlow.value = false
    }

    protected fun isLoadingVisible(): Boolean {
        return isLoadingFlow.value
    }
}

package com.v2ray.ang.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.R
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.CustomDividerItemDecoration
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.Utils


abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (!Utils.getDarkModeStatus(this)) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            // Handles the home button press by delegating to the onBackPressedDispatcher.
            // This ensures consistent back navigation behavior.
            onBackPressedDispatcher.onBackPressed()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }

    /**
     * Adds a custom divider to a RecyclerView.
     *
     * @param recyclerView  The target RecyclerView to which the divider will be added.
     * @param context       The context used to access resources.
     * @param drawableResId The resource ID of the drawable to be used as the divider.
     * @param orientation   The orientation of the divider (DividerItemDecoration.VERTICAL or DividerItemDecoration.HORIZONTAL).
     */
    protected fun addCustomDividerToRecyclerView(recyclerView: RecyclerView, context: Context?, drawableResId: Int, orientation: Int = DividerItemDecoration.VERTICAL) {
        // Get the drawable from resources
        val drawable = ContextCompat.getDrawable(context!!, drawableResId)
        requireNotNull(drawable) { "Drawable resource not found" }

        // Create a DividerItemDecoration with the specified orientation
        val dividerItemDecoration = CustomDividerItemDecoration(drawable, orientation)

        // Add the divider to the RecyclerView
        recyclerView.addItemDecoration(dividerItemDecoration)
    }

    protected fun setupToolbar(toolbar: Toolbar?, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val tb = toolbar ?: findViewById<Toolbar?>(R.id.toolbar)
        tb?.let {
            setSupportActionBar(it)
            supportActionBar?.setDisplayHomeAsUpEnabled(showHomeAsUp)
            title?.let { t -> this.title = t }
        }
    }

    protected fun setContentViewWithToolbar(layoutResId: Int, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val base = LayoutInflater.from(this).inflate(R.layout.activity_base, null)
        val container = base.findViewById<FrameLayout>(R.id.content_container)
        LayoutInflater.from(this).inflate(layoutResId, container, true)
        super.setContentView(base)
        setupToolbar(base, showHomeAsUp, title)
    }

    protected fun setContentViewWithToolbar(childView: View, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val base = LayoutInflater.from(this).inflate(R.layout.activity_base, null)
        val container = base.findViewById<FrameLayout>(R.id.content_container)
        container.addView(childView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        super.setContentView(base)
        setupToolbar(base, showHomeAsUp, title)
    }

    private fun setupToolbar(baseRoot: View, showHomeAsUp: Boolean, title: CharSequence?) {
        val toolbar = baseRoot.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.let {
            setSupportActionBar(it)
            supportActionBar?.setDisplayHomeAsUpEnabled(showHomeAsUp)
            title?.let { t -> supportActionBar?.title = t }
        }
    }

}

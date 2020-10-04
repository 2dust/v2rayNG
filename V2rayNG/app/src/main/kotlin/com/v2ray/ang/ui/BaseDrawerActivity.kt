package com.v2ray.ang.ui

import android.app.ActivityOptions
import android.app.FragmentManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.MenuItem
import android.view.View
import com.v2ray.ang.R

abstract class BaseDrawerActivity : BaseActivity() {
    companion object {

        private val TAG = "BaseDrawerActivity"
    }

    private var mToolbar: Toolbar? = null

    private var mDrawerToggle: ActionBarDrawerToggle? = null

    private var mDrawerLayout: DrawerLayout? = null

    private var mToolbarInitialized: Boolean = false

    private var mItemToOpenWhenDrawerCloses = -1

    private val backStackChangedListener = FragmentManager.OnBackStackChangedListener { updateDrawerToggle() }

    private val drawerListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            mDrawerToggle!!.onDrawerSlide(drawerView, slideOffset)
        }

        override fun onDrawerOpened(drawerView: View) {
            mDrawerToggle!!.onDrawerOpened(drawerView)
            //supportActionBar!!.setTitle(R.string.app_name)
        }

        override fun onDrawerClosed(drawerView: View) {
            mDrawerToggle!!.onDrawerClosed(drawerView)

            if (mItemToOpenWhenDrawerCloses >= 0) {
                val extras = ActivityOptions.makeCustomAnimation(
                        this@BaseDrawerActivity, R.anim.fade_in, R.anim.fade_out).toBundle()
                var activityClass: Class<*>? = null
                when (mItemToOpenWhenDrawerCloses) {
                    R.id.sub_setting -> activityClass = SubSettingActivity::class.java
                    R.id.settings -> activityClass = SettingsActivity::class.java
                    R.id.logcat -> {
                        startActivity(Intent(this@BaseDrawerActivity, LogcatActivity::class.java))
                        return
                    }
                    R.id.donate -> {
//                        startActivity<InappBuyActivity>()
                        return
                    }
                }
                if (activityClass != null) {
                    startActivity(Intent(this@BaseDrawerActivity, activityClass), extras)
                    finish()
                }
            }
        }

        override fun onDrawerStateChanged(newState: Int) {
            mDrawerToggle!!.onDrawerStateChanged(newState)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity onCreate")
    }

    override fun onStart() {
        super.onStart()
        if (!mToolbarInitialized) {
            throw IllegalStateException("You must run super.initializeToolbar at " + "the end of your onCreate method")
        }
    }

    public override fun onResume() {
        super.onResume()
        // Whenever the fragment back stack changes, we may need to update the
        // action bar toggle: only top level screens show the hamburger-like icon, inner
        // screens - either Activities or fragments - show the "Up" icon instead.
        fragmentManager.addOnBackStackChangedListener(backStackChangedListener)
    }

    public override fun onPause() {
        super.onPause()
        fragmentManager.removeOnBackStackChangedListener(backStackChangedListener)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle!!.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle!!.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mDrawerToggle != null && mDrawerToggle!!.onOptionsItemSelected(item)) {
            return true
        }
        // If not handled by drawerToggle, home needs to be handled by returning to previous
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        // If the drawer is open, back will close it
        if (mDrawerLayout != null && mDrawerLayout!!.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout!!.closeDrawers()
            return
        }
        // Otherwise, it may return to the previous fragment stack
        val fragmentManager = fragmentManager
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        } else {
            // Lastly, it will rely on the system behavior for back
            super.onBackPressed()
        }
    }

    private fun updateDrawerToggle() {
        if (mDrawerToggle == null) {
            return
        }
        val isRoot = fragmentManager.backStackEntryCount == 0
        mDrawerToggle!!.isDrawerIndicatorEnabled = isRoot

        supportActionBar!!.setDisplayShowHomeEnabled(!isRoot)
        supportActionBar!!.setDisplayHomeAsUpEnabled(!isRoot)
        supportActionBar!!.setHomeButtonEnabled(!isRoot)

        if (isRoot) {
            mDrawerToggle!!.syncState()
        }
    }

    protected fun initializeToolbar() {
        mToolbar = findViewById<View>(R.id.toolbar) as Toolbar
        if (mToolbar == null) {
            throw IllegalStateException("Layout is required to include a Toolbar with id " + "'toolbar'")
        }

        //        mToolbar.inflateMenu(R.menu.main);

        mDrawerLayout = findViewById<View>(R.id.drawer_layout) as DrawerLayout
        if (mDrawerLayout != null) {
            val navigationView = findViewById<View>(R.id.nav_view) as NavigationView
                    ?: throw IllegalStateException("Layout requires a NavigationView " + "with id 'nav_view'")

            // Create an ActionBarDrawerToggle that will handle opening/closing of the drawer:
            mDrawerToggle = ActionBarDrawerToggle(this, mDrawerLayout,
                    mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)

            mDrawerLayout!!.addDrawerListener(drawerListener)

            populateDrawerItems(navigationView)
            setSupportActionBar(mToolbar)
            updateDrawerToggle()
        } else {
            setSupportActionBar(mToolbar)
        }

        mToolbarInitialized = true
    }

    private fun populateDrawerItems(navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            menuItem.isChecked = true
            mItemToOpenWhenDrawerCloses = menuItem.itemId
            mDrawerLayout!!.closeDrawers()
            true
        }

        if (SubSettingActivity::class.java.isAssignableFrom(javaClass)) {
            navigationView.setCheckedItem(R.id.sub_setting)
        } else if (SettingsActivity::class.java.isAssignableFrom(javaClass)) {
            navigationView.setCheckedItem(R.id.settings)
        }
    }
}

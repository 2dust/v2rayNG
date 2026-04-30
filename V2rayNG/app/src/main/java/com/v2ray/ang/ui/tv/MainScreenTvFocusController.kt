package com.v2ray.ang.ui.tv

import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.core.view.isVisible

class MainScreenTvFocusController(
    private val drawerLayout: DrawerLayout,
    private val navView: NavigationView,
    private val toolbar: Toolbar,
    private val tabGroup: View,
    private val viewPager: View,
    private val fabWrapper: View,
    private val progressBar: View,
    private val toolbarFocusBackgroundResId: Int,
    private val focusCurrentServerList: () -> Boolean,
) {
    private var pendingDrawerFocus = false
    private val drawerListener = object : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerOpened(drawerView: View) {
            pendingDrawerFocus = true
            focusFirstNavigationItem()
        }

        override fun onDrawerClosed(drawerView: View) {
            pendingDrawerFocus = false
        }
    }
    private val globalFocusChangeListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            if (
                drawerLayout.isDrawerOpen(GravityCompat.START) &&
                !pendingDrawerFocus &&
                newFocus != null &&
                !isViewInsideNavigation(newFocus)
            ) {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

    fun attach() {
        drawerLayout.addDrawerListener(drawerListener)
        drawerLayout.viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusChangeListener)
    }

    fun detach() {
        drawerLayout.removeDrawerListener(drawerListener)
        val observer = drawerLayout.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnGlobalFocusChangeListener(globalFocusChangeListener)
        }
    }

    fun configureContentFocus(hasServers: Boolean, hasTabs: Boolean) {
        val toolbarTargets = ToolbarFocusUtils.findActionTargets(toolbar)
        ToolbarFocusUtils.prepareFocusTargets(toolbarTargets, toolbarFocusBackgroundResId)

        val navigationButton = toolbarTargets.firstOrNull()
        val toolbarDownTarget = resolveToolbarDownTarget(hasServers, hasTabs)

        toolbarTargets.forEach { target ->
            target.nextFocusDownId = toolbarDownTarget.id
            target.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                    if (hasServers && focusCurrentServerList()) {
                        return@setOnKeyListener true
                    }
                    toolbarDownTarget.requestFocus()
                    return@setOnKeyListener true
                }
                false
            }
        }

        if (hasTabs) {
            tabGroup.isFocusable = true
            tabGroup.nextFocusDownId = viewPager.id
            fabWrapper.nextFocusUpId = tabGroup.id
            tabGroup.nextFocusUpId = navigationButton?.id ?: View.NO_ID
        } else {
            tabGroup.isFocusable = false
            tabGroup.nextFocusDownId = View.NO_ID
            tabGroup.nextFocusUpId = View.NO_ID
            if (navigationButton != null) {
                fabWrapper.nextFocusUpId = navigationButton.id
                viewPager.nextFocusUpId = navigationButton.id
                navigationButton.nextFocusDownId = fabWrapper.id
            }
        }
    }

    fun handleFabNavigateUp(hasVisibleTabs: Boolean): Boolean {
        val toolbarTarget = findPrimaryToolbarFocusTarget() ?: return false
        return when {
            hasVisibleTabs -> {
                tabGroup.requestFocus()
                true
            }

            progressBar.isVisible -> {
                progressBar.requestFocus()
                true
            }

            else -> {
                toolbarTarget.requestFocus()
                true
            }
        }
    }

    fun handleDrawerHorizontalNavigation(keyCode: Int, currentFocus: View?): Boolean {
        if (
            drawerLayout.isDrawerOpen(GravityCompat.START) &&
            isViewInsideNavigation(currentFocus) &&
            (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        ) {
            closeDrawerAndRestoreFocus()
            return true
        }
        return false
    }

    fun findPrimaryToolbarFocusTarget(): View? {
        val toolbarTargets = ToolbarFocusUtils.findActionTargets(toolbar)
        ToolbarFocusUtils.prepareFocusTargets(toolbarTargets, toolbarFocusBackgroundResId)
        return toolbarTargets.firstOrNull()
    }

    private fun resolveToolbarDownTarget(hasServers: Boolean, hasTabs: Boolean): View {
        return when {
            hasTabs -> tabGroup
            hasServers -> viewPager
            else -> fabWrapper
        }
    }

    private fun focusFirstNavigationItem() {
        val firstItemId = findFirstEnabledNavigationItemId() ?: run {
            pendingDrawerFocus = false
            return
        }

        navView.post {
            val target = navView.findViewById<View>(firstItemId)
            if (target != null) {
                target.isFocusable = true
                target.isFocusableInTouchMode = true
                target.requestFocus()
            }
            pendingDrawerFocus = false
        }
    }

    private fun findFirstEnabledNavigationItemId(): Int? {
        val menu = navView.menu
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            if (item.isEnabled) {
                return item.itemId
            }
        }
        return null
    }

    private fun isViewInsideNavigation(view: View?): Boolean {
        var current = view
        while (current != null) {
            if (current === navView) return true
            current = current.parent as? View
        }
        return false
    }

    private fun closeDrawerAndRestoreFocus() {
        drawerLayout.closeDrawer(GravityCompat.START)
        drawerLayout.post {
            val fallbackTarget = findPrimaryToolbarFocusTarget() ?: fabWrapper
            fallbackTarget.requestFocus()
        }
    }
}

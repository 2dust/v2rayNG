package com.v2ray.ang.ui.tv

import android.view.KeyEvent
import android.view.View
import androidx.appcompat.widget.Toolbar

object FormTvFocusBinder {
    fun bind(toolbar: Toolbar?, orderedViews: List<View?>) {
        val formViews = orderedViews
            .filterNotNull()
            .filter { it.isActuallyFocusable() }

        val toolbarTargets = toolbar?.let { ToolbarFocusUtils.findActionTargets(it) }.orEmpty()
        ToolbarFocusUtils.prepareFocusTargets(toolbarTargets)

        val firstToolbarTarget = toolbarTargets.firstOrNull()
        val lastToolbarTarget = toolbarTargets.lastOrNull()

        formViews.forEachIndexed { index, view ->
            ToolbarFocusUtils.ensureViewId(view)
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            val previous = formViews.getOrNull(index - 1) ?: lastToolbarTarget
            val next = formViews.getOrNull(index + 1) ?: firstToolbarTarget

            view.nextFocusUpId = previous?.id ?: View.NO_ID
            view.nextFocusDownId = next?.id ?: View.NO_ID
        }

        if (formViews.isNotEmpty()) {
            val firstFormView = formViews.first()
            val firstFormViewId = firstFormView.id
            toolbarTargets.forEachIndexed { index, view ->
                view.nextFocusUpId = toolbarTargets.getOrNull(index - 1)?.id ?: View.NO_ID
                view.nextFocusDownId = firstFormViewId
                view.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                        firstFormView.requestFocus()
                        return@setOnKeyListener true
                    }
                    false
                }
            }

            firstFormView.nextFocusUpId = lastToolbarTarget?.id ?: View.NO_ID
            formViews.last().nextFocusDownId = firstToolbarTarget?.id ?: View.NO_ID
        }
    }

    private fun View.isActuallyFocusable(): Boolean {
        if (visibility != View.VISIBLE || !isEnabled) return false

        var current: View? = this
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            val parent = current.parent
            current = if (parent is View) parent else null
        }
        return true
    }
}

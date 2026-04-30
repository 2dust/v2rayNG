package com.v2ray.ang.ui.tv

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar

object ToolbarFocusUtils {
    fun findActionTargets(toolbar: Toolbar): List<View> {
        val targets = mutableListOf<View>()

        fun collect(view: View) {
            if (view.visibility != View.VISIBLE) return

            val isToolbarActionTarget =
                view !== toolbar &&
                    (
                        view is ImageButton ||
                            (view.isClickable && !view.contentDescription.isNullOrBlank())
                        )

            if (isToolbarActionTarget) {
                ensureViewId(view)
                targets.add(view)
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    collect(view.getChildAt(index))
                }
            }
        }

        collect(toolbar)
        return targets.distinctBy { it.id }
    }

    fun prepareFocusTargets(targets: List<View>, backgroundResId: Int? = null) {
        targets.forEach { view ->
            ensureViewId(view)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            if (backgroundResId != null) {
                view.setBackgroundResource(backgroundResId)
            }
        }
    }

    fun ensureViewId(view: View) {
        if (view.id == View.NO_ID) {
            view.id = View.generateViewId()
        }
    }
}

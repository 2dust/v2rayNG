package com.v2ray.ang.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageButton
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.v2ray.ang.R

class CopyablePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    var onCopyClick: (() -> Unit)? = null

    init {
        widgetLayoutResource = R.layout.preference_copy_button
    }

    fun refreshCopyButton() {
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val copyButton = holder.findViewById(R.id.copy_button) as? ImageButton ?: return
        val hasValue = !summary.isNullOrEmpty()

        copyButton.visibility = if (isVisible) View.VISIBLE else View.GONE
        copyButton.isEnabled = hasValue
        copyButton.alpha = if (hasValue) 1f else 0.4f
        copyButton.setOnClickListener {
            if (hasValue) {
                onCopyClick?.invoke()
            }
        }
    }
}

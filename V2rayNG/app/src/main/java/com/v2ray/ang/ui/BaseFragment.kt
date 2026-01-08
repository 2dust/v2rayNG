package com.v2ray.ang.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.v2ray.ang.helper.CustomDividerItemDecoration

abstract class BaseFragment<VB : ViewBinding> : Fragment() {
    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding!!

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Adds a custom divider to a RecyclerView.
     *
     * @param recyclerView  The target RecyclerView to which the divider will be added.
     * @param drawableResId The resource ID of the drawable to be used as the divider.
     * @param orientation   The orientation of the divider (DividerItemDecoration.VERTICAL or DividerItemDecoration.HORIZONTAL).
     */
    fun addCustomDividerToRecyclerView(recyclerView: RecyclerView,  drawableResId: Int, orientation: Int = DividerItemDecoration.VERTICAL) {
        // Get the drawable from resources
        val drawable = ContextCompat.getDrawable(requireContext(), drawableResId)
        requireNotNull(drawable) { "Drawable resource not found" }

        // Create a DividerItemDecoration with the specified orientation
        val dividerItemDecoration = CustomDividerItemDecoration(drawable, orientation)

        // Add the divider to the RecyclerView
        recyclerView.addItemDecoration(dividerItemDecoration)
    }
}
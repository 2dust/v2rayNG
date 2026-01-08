package com.v2ray.ang.ui

import android.annotation.SuppressLint
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.v2ray.ang.dto.GroupMapItem

/**
 * Pager adapter for subscription groups.
 */
class GroupPagerAdapter(activity: FragmentActivity, var groups: List<GroupMapItem>) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = groups.size
    override fun createFragment(position: Int) = GroupServerFragment.newInstance(groups[position].id)

    @SuppressLint("NotifyDataSetChanged")
    fun update(groups: List<GroupMapItem>) {
        this.groups = groups
        notifyDataSetChanged()
    }
}

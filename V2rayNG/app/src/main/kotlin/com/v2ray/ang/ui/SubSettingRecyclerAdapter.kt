package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.v2ray.ang.databinding.ItemRecyclerSubSettingBinding

class SubSettingRecyclerAdapter(val activity: SubSettingActivity) : RecyclerView.Adapter<SubSettingRecyclerAdapter.MainViewHolder>() {

    private var mActivity: SubSettingActivity = activity

    override fun getItemCount() = mActivity.subscriptions.size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val subId = mActivity.subscriptions[position].first
        val subItem = mActivity.subscriptions[position].second
        holder.itemSubSettingBinding.tvName.text = subItem.remarks
        holder.itemSubSettingBinding.tvUrl.text = subItem.url
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        holder.itemSubSettingBinding.layoutEdit.setOnClickListener {
            mActivity.startActivity(Intent(mActivity, SubEditActivity::class.java)
                    .putExtra("subId", subId)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(ItemRecyclerSubSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerSubSettingBinding) : RecyclerView.ViewHolder(itemSubSettingBinding.root)
}

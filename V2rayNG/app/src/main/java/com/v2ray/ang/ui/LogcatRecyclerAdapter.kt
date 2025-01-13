package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemRecyclerLogcatBinding

class LogcatRecyclerAdapter(val activity: LogcatActivity) : RecyclerView.Adapter<LogcatRecyclerAdapter.MainViewHolder>() {
    private var mActivity: LogcatActivity = activity

    override fun getItemCount() = mActivity.logsets.size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val content = mActivity.logsets[position]
        holder.itemSubSettingBinding.logContent.text = content

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerLogcatBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerLogcatBinding) : RecyclerView.ViewHolder(itemSubSettingBinding.root)

}

package com.v2ray.ang.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.databinding.ItemRecyclerLogcatBinding
import com.v2ray.ang.viewmodel.LogcatViewModel

class LogcatRecyclerAdapter(
    private val viewModel: LogcatViewModel,
    private val onLongClick: ((String) -> Boolean)? = null
) : RecyclerView.Adapter<LogcatRecyclerAdapter.MainViewHolder>() {

    override fun getItemCount() = viewModel.getAll().size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        try {
            val logs = viewModel.getAll()
            val log = logs[position]

            if (log.isEmpty()) {
                holder.itemSubSettingBinding.logTag.text = ""
                holder.itemSubSettingBinding.logContent.text = ""
            } else {
                val content = log.split("):", limit = 2)
                holder.itemSubSettingBinding.logTag.text = content.first().split("(", limit = 2).first().trim()
                holder.itemSubSettingBinding.logContent.text = if (content.count() > 1) content.last().trim() else ""
            }

            holder.itemView.setOnLongClickListener {
                onLongClick?.invoke(log) ?: false
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Error binding log view data", e)
        }
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

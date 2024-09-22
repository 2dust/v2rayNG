package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemRecyclerRoutingSettingBinding
import com.v2ray.ang.util.SettingsManager

class RoutingSettingRecyclerAdapter(val activity: RoutingSettingActivity) :
    RecyclerView.Adapter<RoutingSettingRecyclerAdapter.MainViewHolder>() {

    private var mActivity: RoutingSettingActivity = activity
    override fun getItemCount() = mActivity.rulesets.size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val ruleset = mActivity.rulesets[position]

        holder.itemRoutingSettingBinding.remarks.text = ruleset.remarks
        holder.itemRoutingSettingBinding.domainIp.text = (ruleset.domain ?: ruleset.ip ?: ruleset.port)?.toString()
        holder.itemRoutingSettingBinding.outboundTag.text = ruleset.outboundTag
        holder.itemRoutingSettingBinding.chkEnable.isChecked = ruleset.enabled
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        holder.itemRoutingSettingBinding.layoutEdit.setOnClickListener {
            mActivity.startActivity(
                Intent(mActivity, RoutingEditActivity::class.java)
                    .putExtra("position", position)
            )
        }

        holder.itemRoutingSettingBinding.chkEnable.setOnCheckedChangeListener { _, isChecked ->
            ruleset.enabled = isChecked
            SettingsManager.saveRoutingRuleset(position, ruleset)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerRoutingSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class MainViewHolder(val itemRoutingSettingBinding: ItemRecyclerRoutingSettingBinding) :
        RecyclerView.ViewHolder(itemRoutingSettingBinding.root)
}

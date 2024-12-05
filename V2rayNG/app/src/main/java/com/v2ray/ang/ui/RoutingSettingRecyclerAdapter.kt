package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemRecyclerRoutingSettingBinding
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder

class RoutingSettingRecyclerAdapter(val activity: RoutingSettingActivity) : RecyclerView.Adapter<RoutingSettingRecyclerAdapter.MainViewHolder>(),
    ItemTouchHelperAdapter {

    private var mActivity: RoutingSettingActivity = activity
    override fun getItemCount() = mActivity.rulesets.size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val ruleset = mActivity.rulesets[position]

        holder.itemRoutingSettingBinding.remarks.text = ruleset.remarks
        holder.itemRoutingSettingBinding.domainIp.text = (ruleset.domain ?: ruleset.ip ?: ruleset.port)?.toString()
        holder.itemRoutingSettingBinding.outboundTag.text = ruleset.outboundTag
        holder.itemRoutingSettingBinding.chkEnable.isChecked = ruleset.enabled
        holder.itemRoutingSettingBinding.imgLocked.isVisible = ruleset.locked == true
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        holder.itemRoutingSettingBinding.layoutEdit.setOnClickListener {
            mActivity.startActivity(
                Intent(mActivity, RoutingEditActivity::class.java)
                    .putExtra("position", position)
            )
        }

        holder.itemRoutingSettingBinding.chkEnable.setOnCheckedChangeListener { it, isChecked ->
            if (!it.isPressed) return@setOnCheckedChangeListener
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
        BaseViewHolder(itemRoutingSettingBinding.root), ItemTouchHelperViewHolder

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        SettingsManager.swapRoutingRuleset(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        mActivity.refreshData()
    }

    override fun onItemDismiss(position: Int) {
    }
}

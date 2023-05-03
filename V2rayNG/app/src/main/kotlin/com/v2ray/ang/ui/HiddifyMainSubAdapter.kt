package com.v2ray.ang.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.v2ray.ang.R
import com.v2ray.ang.extension.click
import com.v2ray.ang.extension.getColorEx
import com.v2ray.ang.extension.showGone
import com.v2ray.ang.util.HiddifyUtils
import com.v2ray.ang.util.MmkvManager

class HiddifyMainSubAdapter(val context: Context, val callback: (Int,Boolean) -> Any) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    public var subscriptions = MmkvManager.decodeSubscriptions()

    override fun getItemCount(): Int {
        return subscriptions.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_recycler_sub_hiddify_main, parent, false)
        return SubViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as SubViewHolder).apply {
            val subItem = subscriptions[position].second

            profileName.text = subItem.remarks

            time.text =
                HiddifyUtils.timeToRelativeDate(subItem.expire, subItem.total, subItem.used, context)
            time.showGone(subItem.expire > (0).toLong())

            consumerTrafficValue.text = HiddifyUtils.toTotalUsedGig(
                subItem.total,
                subItem.used,
                context
            )
            consumerTrafficValue.showGone(subItem.total > (0).toLong())

            progress.progress = (subItem.used / 1000000000).toInt()
            progress.max = (subItem.total / 1000000000).toInt()
            progress.showGone(subItem.total > (0).toLong())
            itemBg.backgroundTintList = if (HiddifyUtils.checkState(subItem.expire, subItem.total, subItem.used) == "enable")
                ColorStateList.valueOf(context.getColorEx(R.color.colorBtnBg))
            else
                ColorStateList.valueOf(context.getColorEx(R.color.colorLightRed))

            itemView.click {
                callback.invoke(bindingAdapterPosition,false)
            }
            itemView.setOnLongClickListener {
                callback.invoke(bindingAdapterPosition,true)
                return@setOnLongClickListener true
            }
        }
    }

    private inner class SubViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val time: TextView = itemView.findViewById(R.id.time)
        val profileName: TextView = itemView.findViewById(R.id.profileName)
        val consumerTrafficValue: TextView = itemView.findViewById(R.id.consumerTrafficValue)
        val progress: LinearProgressIndicator = itemView.findViewById(R.id.progress)
        val itemBg: CardView = itemView.findViewById(R.id.item_bg)
    }
}

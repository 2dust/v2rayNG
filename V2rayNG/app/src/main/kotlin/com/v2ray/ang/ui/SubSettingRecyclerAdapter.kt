package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.v2ray.ang.R
import kotlinx.android.synthetic.main.item_recycler_sub_setting.view.*

class SubSettingRecyclerAdapter(val activity: SubSettingActivity) : RecyclerView.Adapter<SubSettingRecyclerAdapter.BaseViewHolder>() {

    private var mActivity: SubSettingActivity = activity

    override fun getItemCount() = mActivity.subscriptions.size

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val subId = mActivity.subscriptions[position].first
            val subItem = mActivity.subscriptions[position].second
            holder.name.text = subItem.remarks
            holder.url.text = subItem.url
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)

            holder.layout_edit.setOnClickListener {
                mActivity.startActivity(Intent(mActivity, SubEditActivity::class.java)
                        .putExtra("subId", subId)
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return MainViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recycler_sub_setting, parent, false))
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class MainViewHolder(itemView: View) : BaseViewHolder(itemView) {
        val name = itemView.tv_name!!
        val url = itemView.tv_url!!
        val layout_edit = itemView.layout_edit!!
    }

}

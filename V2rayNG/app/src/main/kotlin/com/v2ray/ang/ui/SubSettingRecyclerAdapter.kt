package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.v2ray.ang.R
import com.v2ray.ang.dto.AngConfig
import com.v2ray.ang.util.AngConfigManager
import kotlinx.android.synthetic.main.item_recycler_sub_setting.view.*

class SubSettingRecyclerAdapter(val activity: SubSettingActivity) : RecyclerView.Adapter<SubSettingRecyclerAdapter.BaseViewHolder>() {

    private var mActivity: SubSettingActivity = activity
    private lateinit var configs: AngConfig

    init {
        updateConfigList()
    }

    override fun getItemCount() = configs.subItem.count()

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val remarks = configs.subItem[position].remarks
            val url = configs.subItem[position].url

            holder.name.text = remarks
            holder.url.text = url
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)

            holder.layout_edit.setOnClickListener {
                mActivity.startActivity(Intent(mActivity, SubEditActivity::class.java)
                        .putExtra("position", position)
                )
            }
        } else {
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return MainViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recycler_sub_setting, parent, false))
    }

    fun updateConfigList() {
        configs = AngConfigManager.configs
        notifyDataSetChanged()
    }

//    fun updateSelectedItem() {
//        notifyItemChanged(configs.index)
//    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class MainViewHolder(itemView: View) : BaseViewHolder(itemView) {
        val name = itemView.tv_name!!
        val url = itemView.tv_url!!
        val layout_edit = itemView.layout_edit!!
    }

}

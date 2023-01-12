package com.v2ray.ang.ui

import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemRecyclerBypassListBinding
import com.v2ray.ang.dto.AppInfo
import java.util.*

class PerAppProxyAdapter(val activity: BaseActivity, val apps: List<AppInfo>, blacklist: MutableSet<String>?) :
        RecyclerView.Adapter<PerAppProxyAdapter.BaseViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    val blacklist = if (blacklist == null) HashSet() else HashSet(blacklist)

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is AppViewHolder) {
            val appInfo = apps[position - 1]
            holder.bind(appInfo)
        }
    }

    override fun getItemCount() = apps.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val ctx = parent.context

        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = View(ctx)
                view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ctx.resources.getDimensionPixelSize(R.dimen.bypass_list_header_height) * 0)
                BaseViewHolder(view)
            }
//            VIEW_TYPE_ITEM -> AppViewHolder(ctx.layoutInflater
//                    .inflate(R.layout.item_recycler_bypass_list, parent, false))

            else -> AppViewHolder(ItemRecyclerBypassListBinding.inflate(LayoutInflater.from(ctx), parent, false))

        }
    }

    override fun getItemViewType(position: Int) = if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class AppViewHolder(private val itemBypassBinding: ItemRecyclerBypassListBinding) : BaseViewHolder(itemBypassBinding.root),
            View.OnClickListener {
        private val inBlacklist: Boolean get() = blacklist.contains(appInfo.packageName)
        private lateinit var appInfo: AppInfo

        fun bind(appInfo: AppInfo) {
            this.appInfo = appInfo

            itemBypassBinding.icon.setImageDrawable(appInfo.appIcon)
//            name.text = appInfo.appName

            itemBypassBinding.checkBox.isChecked = inBlacklist
            itemBypassBinding.packageName.text = appInfo.packageName
            if (appInfo.isSystemApp) {
                itemBypassBinding.name.text = String.format("** %1s", appInfo.appName)
                //name.textColor = Color.RED
            } else {
                itemBypassBinding.name.text = appInfo.appName
                //name.textColor = Color.DKGRAY
            }

            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            if (inBlacklist) {
                blacklist.remove(appInfo.packageName)
                itemBypassBinding.checkBox.isChecked = false
            } else {
                blacklist.add(appInfo.packageName)
                itemBypassBinding.checkBox.isChecked = true
            }
        }
    }
}

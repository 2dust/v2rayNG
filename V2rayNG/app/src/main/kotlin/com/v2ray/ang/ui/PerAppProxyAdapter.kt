package com.v2ray.ang.ui

import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import kotlinx.android.synthetic.main.item_recycler_bypass_list.view.*
import java.util.*

class PerAppProxyAdapter(val activity: BaseActivity, val apps: List<AppInfo>, blacklist: MutableSet<String>?) :
        RecyclerView.Adapter<PerAppProxyAdapter.BaseViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    private var mActivity: BaseActivity = activity
    val blacklist = if (blacklist == null) HashSet<String>() else HashSet<String>(blacklist)

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
                        ctx.resources.getDimensionPixelSize(R.dimen.bypass_list_header_height) * 3)
                BaseViewHolder(view)
            }
//            VIEW_TYPE_ITEM -> AppViewHolder(ctx.layoutInflater
//                    .inflate(R.layout.item_recycler_bypass_list, parent, false))

            else -> AppViewHolder(LayoutInflater.from(ctx)
                    .inflate(R.layout.item_recycler_bypass_list, parent, false))

        }
    }

    override fun getItemViewType(position: Int) = if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class AppViewHolder(itemView: View) : BaseViewHolder(itemView),
            View.OnClickListener {
        private val inBlacklist: Boolean get() = blacklist.contains(appInfo.packageName)
        private lateinit var appInfo: AppInfo

        val icon = itemView.icon!!
        val name = itemView.name!!
        val package_name = itemView.package_name!!
        val checkBox = itemView.check_box!!

        fun bind(appInfo: AppInfo) {
            this.appInfo = appInfo

            icon.setImageDrawable(appInfo.appIcon)
//            name.text = appInfo.appName

            checkBox.isChecked = inBlacklist
            package_name.text = appInfo.packageName
            if (appInfo.isSystemApp) {
                name.text = String.format("** %1s", appInfo.appName)
                name.setTextColor(Color.RED)
            } else {
                name.text = appInfo.appName
                name.setTextColor(Color.DKGRAY)
            }

            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            if (inBlacklist) {
                blacklist.remove(appInfo.packageName)
                checkBox.isChecked = false
            } else {
                blacklist.add(appInfo.packageName)
                checkBox.isChecked = true
            }
        }
    }
}

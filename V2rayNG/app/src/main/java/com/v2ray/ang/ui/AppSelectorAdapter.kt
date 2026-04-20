package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemRecyclerBypassListBinding
import com.v2ray.ang.dto.AppInfo

class AppSelectorAdapter(
    private val selectedPackages: MutableSet<String>
) : RecyclerView.Adapter<AppSelectorAdapter.AppViewHolder>() {

    var apps: List<AppInfo> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemRecyclerBypassListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    fun submitList(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    fun refreshSelection() {
        notifyDataSetChanged()
    }

    inner class AppViewHolder(private val binding: ItemRecyclerBypassListBinding) : RecyclerView.ViewHolder(binding.root),
        View.OnClickListener {
        private lateinit var appInfo: AppInfo

        fun bind(item: AppInfo) {
            appInfo = item
            binding.icon.setImageDrawable(item.appIcon)
            binding.name.text = if (item.isSystemApp) {
                String.format("** %s", item.appName)
            } else {
                item.appName
            }
            binding.packageName.text = item.packageName
            binding.checkBox.isChecked = selectedPackages.contains(item.packageName)
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val packageName = appInfo.packageName
            if (selectedPackages.contains(packageName)) {
                selectedPackages.remove(packageName)
            } else {
                selectedPackages.add(packageName)
            }
            binding.checkBox.isChecked = selectedPackages.contains(packageName)
        }
    }
}


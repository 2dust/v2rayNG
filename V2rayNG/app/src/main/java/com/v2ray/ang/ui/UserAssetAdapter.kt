package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerUserAssetBinding
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.viewmodel.UserAssetViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date

class UserAssetAdapter(
    private val viewModel: UserAssetViewModel,
    private val extDir: File,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<UserAssetAdapter.UserAssetViewHolder>() {

    override fun getItemCount() = viewModel.itemCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAssetViewHolder {
        return UserAssetViewHolder(
            ItemRecyclerUserAssetBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: UserAssetViewHolder, position: Int) {
        val item = viewModel.getAsset(position) ?: return
        val file = extDir.listFiles()?.find { it.name == item.assetUrl.remarks }

        holder.itemUserAssetBinding.assetName.text = item.assetUrl.remarks

        if (file != null) {
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
            holder.itemUserAssetBinding.assetProperties.text =
                "${file.length().toTrafficString()}  •  ${dateFormat.format(Date(file.lastModified()))}"
        } else {
            holder.itemUserAssetBinding.assetProperties.text =
                holder.itemUserAssetBinding.root.context.getString(R.string.msg_file_not_found)
        }

        if (item.assetUrl.locked == true) {
            holder.itemUserAssetBinding.layoutEdit.visibility = View.GONE
        } else {
            holder.itemUserAssetBinding.layoutEdit.visibility = if (item.assetUrl.url == "file") {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        holder.itemUserAssetBinding.layoutEdit.setOnClickListener {
            adapterListener?.onEdit(item.guid, position)
        }
        holder.itemUserAssetBinding.layoutRemove.setOnClickListener {
            adapterListener?.onRemove(item.guid, position)
        }
    }

    class UserAssetViewHolder(val itemUserAssetBinding: ItemRecyclerUserAssetBinding) :
        RecyclerView.ViewHolder(itemUserAssetBinding.root)
}

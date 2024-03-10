package com.v2ray.ang.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.databinding.ItemRecyclerIpScannerBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils

class IpScannerRecyclerAdapter(val activity: IpScannerActivity) :
    RecyclerView.Adapter<IpScannerRecyclerAdapter.MainViewHolder>() {

    private var mActivity: IpScannerActivity = activity
//    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }
//
//    private val share_method: Array<out String> by lazy {
//        mActivity.resources.getStringArray(R.array.share_sub_method)
//    }

    override fun getItemCount() = mActivity.cleanIps.size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val ip = mActivity.cleanIps[position].first
        var latency = "Testing"
        if (mActivity.cleanIps[position].second > 0) {
            latency = mActivity.cleanIps[position].second.toString() + "ms"

            holder.itemRecyclerIpScannerBinding.tvLatency.setTextColor(ContextCompat.getColor(mActivity, R.color.colorPing))
            holder.itemRecyclerIpScannerBinding.tvIp.setTextColor(ContextCompat.getColor(mActivity, R.color.colorText))
            holder.itemRecyclerIpScannerBinding.layoutCopy.visibility = View.VISIBLE
//            holder.itemRecyclerIpScannerBinding.layoutDelete.visibility = View.VISIBLE
//            holder.itemRecyclerIpScannerBinding.layoutDelete.setOnClickListener {
//                Utils.setClipboard(mActivity, ip)
//                mActivity.toast(R.string.toast_success)
//            }
            holder.itemRecyclerIpScannerBinding.layoutCopy.setOnClickListener {
                Utils.setClipboard(mActivity, ip)
                mActivity.toast(R.string.toast_success)
            }
        } else {
            holder.itemRecyclerIpScannerBinding.tvIp.setTextColor(ContextCompat.getColor(mActivity, R.color.color_secondary))
            holder.itemRecyclerIpScannerBinding.tvLatency.setTextColor(ContextCompat.getColor(mActivity, R.color.colorPingRed))
        }
        holder.itemRecyclerIpScannerBinding.tvIp.text = ip
        holder.itemRecyclerIpScannerBinding.tvLatency.text = latency
//        if (subItem.enabled) {
//            holder.itemSubSettingBinding.chkEnable.setBackgroundResource(R.color.colorSelected)
//        } else {
//            holder.itemSubSettingBinding.chkEnable.setBackgroundResource(R.color.colorUnselected)
//        }
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

//        holder.itemSubSettingBinding.layoutEdit.setOnClickListener {
//            mActivity.startActivity(
//                Intent(mActivity, SubEditActivity::class.java)
//                    .putExtra("subId", subId)
//            )
//        }
//        holder.itemSubSettingBinding.infoContainer.setOnClickListener {
//            subItem.enabled = !subItem.enabled
//            subStorage?.encode(subId, Gson().toJson(subItem))
//            notifyItemChanged(position)
//        }
//
//        if (TextUtils.isEmpty(subItem.url)) {
//            holder.itemSubSettingBinding.layoutShare.visibility = View.INVISIBLE
//        } else {
//            holder.itemSubSettingBinding.layoutShare.setOnClickListener {
//                AlertDialog.Builder(mActivity)
//                    .setItems(share_method.asList().toTypedArray()) { _, i ->
//                        try {
//                            when (i) {
//                                0 -> {
//                                    val ivBinding =
//                                        ItemQrcodeBinding.inflate(LayoutInflater.from(mActivity))
//                                    ivBinding.ivQcode.setImageBitmap(
//                                        QRCodeDecoder.createQRCode(
//                                            subItem.url
//
//                                        )
//                                    )
//                                    AlertDialog.Builder(mActivity).setView(ivBinding.root).show()
//                                }
//
//                                1 -> {
//                                    Utils.setClipboard(mActivity, subItem.url)
//                                }
//
//                                else -> mActivity.toast("else")
//                            }
//                        } catch (e: Exception) {
//                            e.printStackTrace()
//                        }
//                    }.show()
//            }
//        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerIpScannerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class MainViewHolder(val itemRecyclerIpScannerBinding: ItemRecyclerIpScannerBinding) :
        RecyclerView.ViewHolder(itemRecyclerIpScannerBinding.root)
}

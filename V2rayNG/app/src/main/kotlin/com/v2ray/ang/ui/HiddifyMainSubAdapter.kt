package com.v2ray.ang.ui

import android.database.DataSetObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SpinnerAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tencent.mmkv.MMKV
import com.v2ray.ang.databinding.ItemRecyclerSubMainBinding
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils


class HiddifyMainSubAdapter(val activity: HiddifyMainActivity) :SpinnerAdapter {

    private var mActivity: HiddifyMainActivity = activity
    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }


    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerSubMainBinding) : RecyclerView.ViewHolder(itemSubSettingBinding.root)

    override fun registerDataSetObserver(p0: DataSetObserver?) {
        return ;
    }

    override fun unregisterDataSetObserver(p0: DataSetObserver?) {
        return ;
    }

    override fun getCount(): Int {
        return mActivity.mainViewModel.subscriptions.size
    }

    override fun getItem(p0: Int): Any {
        return mActivity.mainViewModel.subscriptions[p0]
    }

    override fun getItemId(p0: Int): Long {
        return p0.toLong();
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var holder: MainViewHolder
        if (convertView == null) {

            var convertView_=ItemRecyclerSubMainBinding.inflate(LayoutInflater.from(parent!!.context), parent, false)
            holder = MainViewHolder(convertView_)
            convertView_.root.setTag(holder)
        } else {
            holder = convertView.getTag() as MainViewHolder
        }
        editView(holder.itemSubSettingBinding,position,false)
        return holder.itemSubSettingBinding.root
    }

    override fun getItemViewType(p0: Int): Int {
        return 1
    }

    fun editView(itemSubSettingBinding:ItemRecyclerSubMainBinding, position: Int,clickable:Boolean=true){
        val subId = mActivity.mainViewModel.subscriptions[position].first
        val subItem = mActivity.mainViewModel.subscriptions[position].second
        itemSubSettingBinding.tvName.text = subItem.remarks
        if (subItem.used<0){
            itemSubSettingBinding.usageProgress.visibility=View.INVISIBLE
            itemSubSettingBinding.expireDate.visibility=View.GONE
        }else {
            itemSubSettingBinding.usageProgress.visibility=View.VISIBLE
            itemSubSettingBinding.expireDate.visibility=View.VISIBLE
            itemSubSettingBinding.usageProgress.progress = (subItem.used / 1000000000).toInt()
            itemSubSettingBinding.usageProgress.max = (subItem.total / 1000000000).toInt()
            var days = Utils.timeToRelativeDate(subItem.expire)
            itemSubSettingBinding.expireDate.text =
                days + " " + Utils.toGig(subItem.used) + "/" + Utils.toGig(subItem.total)
        }
        var tmp = subItem.url
//        tmp+="\nused="+subItem.used+"\ntotal="+subItem.total+"\nexpire="+ Utils.timeToRelativeDate(subItem.expire)+"\nhome="+subItem.home_link
//        holder.itemSubSettingBinding.tvUrl.text=tmp
//        if (subItem.enabled) {
//            holder.itemSubSettingBinding.chkEnable.setBackgroundResource(R.color.colorSelected)
//        } else {
//            holder.itemSubSettingBinding.chkEnable.setBackgroundResource(R.color.colorUnselected)
//        }
//        itemSubSettingBinding.setBackgroundColor(Color.TRANSPARENT)



    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun isEmpty(): Boolean {
        return count==0
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var holder: MainViewHolder
        if (convertView == null) {

            var convertView_=ItemRecyclerSubMainBinding.inflate(LayoutInflater.from(parent!!.context), parent, false)
            holder = MainViewHolder(convertView_)
            convertView_.root.setTag(holder)
        } else {
            holder = convertView.getTag() as MainViewHolder
        }
        editView(holder.itemSubSettingBinding,position,true)
        return holder.itemSubSettingBinding.root
    }
}

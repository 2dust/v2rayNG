package com.v2ray.ang.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerProxyChainMemberBinding
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import java.util.Collections

class ServerProxyChainMemberAdapter(
    private val members: MutableList<String>,
    private val suggestions: List<String>,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<ServerProxyChainMemberAdapter.MemberViewHolder>(), ItemTouchHelperAdapter {

    override fun getItemCount(): Int = members.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        return MemberViewHolder(
            ItemRecyclerProxyChainMemberBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val value = members[position]
        holder.binding.tvMemberIndex.text = (position + 1).toString()

        val dropdownAdapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_dropdown_item_1line,
            suggestions
        )
        holder.binding.spMemberRemark.setAdapter(dropdownAdapter)
        holder.binding.spMemberRemark.threshold = 0
        holder.binding.spMemberRemark.setText(value, false)

        holder.binding.spMemberRemark.setOnItemClickListener { parent, _, selectedIndex, _ ->
            val currentPos = holder.bindingAdapterPosition
            val selectedValue = parent.getItemAtPosition(selectedIndex) as? String ?: return@setOnItemClickListener
            if (currentPos != RecyclerView.NO_POSITION && currentPos in members.indices) {
                members[currentPos] = selectedValue.trim()
                adapterListener?.onRefreshData()
            }
        }
        holder.binding.spMemberRemark.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@OnFocusChangeListener
            val currentPos = holder.bindingAdapterPosition
            val text = holder.binding.spMemberRemark.text?.toString().orEmpty().trim()
            if (currentPos != RecyclerView.NO_POSITION && currentPos in members.indices && members[currentPos] != text) {
                members[currentPos] = text
                adapterListener?.onRefreshData()
            }
        }
        holder.binding.spMemberRemark.setOnClickListener { holder.binding.spMemberRemark.showDropDown() }
        holder.binding.btnMemberDropdown.setOnClickListener {
            holder.binding.spMemberRemark.requestFocus()
            holder.binding.spMemberRemark.showDropDown()
        }
        holder.itemView.setOnClickListener { holder.binding.spMemberRemark.showDropDown() }

        holder.binding.layoutRemove.setOnClickListener {
            val removePos = holder.bindingAdapterPosition
            if (removePos != RecyclerView.NO_POSITION) {
                adapterListener?.onRemove("", removePos)
            }
        }
    }

    fun addRow() {
        members.add("")
        notifyItemInserted(members.lastIndex)
        adapterListener?.onRefreshData()
    }

    fun removeRow(position: Int) {
        if (position < 0 || position >= members.size) return
        members.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, members.size - position)
        adapterListener?.onRefreshData()
    }

    fun setRemark(position: Int, remark: String) {
        if (position < 0 || position >= members.size) return
        members[position] = remark
        notifyItemChanged(position)
        adapterListener?.onRefreshData()
    }

    fun replaceAll(newMembers: List<String>) {
        members.clear()
        members.addAll(newMembers)
        notifyDataSetChanged()
        adapterListener?.onRefreshData()
    }

    fun getMembers(): List<String> = members.toList()

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition == toPosition) return true
        Collections.swap(members, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        notifyItemChanged(fromPosition)
        notifyItemChanged(toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        adapterListener?.onRefreshData()
    }

    override fun onItemDismiss(position: Int) {
        // Swipe-to-dismiss disabled for this adapter.
    }

    class MemberViewHolder(val binding: ItemRecyclerProxyChainMemberBinding) :
        BaseViewHolder(binding.root), ItemTouchHelperViewHolder

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }
}


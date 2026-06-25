package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemServerGalaxyTunnelBinding
import com.v2ray.ang.dto.entities.ServersCache

class ServerAdapterGalaxyTunnel(
    private val onSelect: (ServersCache) -> Unit,
    private val onEdit: (ServersCache) -> Unit,
    private val onDelete: (ServersCache) -> Unit,
    private val onTest: (ServersCache) -> Unit
) : ListAdapter<ServersCache, ServerAdapterGalaxyTunnel.ServerViewHolder>(DiffCallback()) {

    private var selectedGuid = ""

    inner class ServerViewHolder(
        val binding: ItemServerGalaxyTunnelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ServersCache, position: Int) {
            val server = item.profile
            
            binding.tvServerName.text = server.remarks
            binding.tvServerProtocol.text = "${server.configType.protocolScheme} • ${server.network ?: "tcp"}"

            // Latency badge
            binding.tvLatency.text = "--"
            binding.tvLatency.background = ContextCompat.getDrawable(itemView.context, R.drawable.gt_latency_badge_medium)

            // Radio button
            binding.radioServer.isChecked = item.guid == selectedGuid

            // Click handlers
            binding.root.setOnClickListener {
                selectedGuid = item.guid
                notifyDataSetChanged()
                onSelect(item)
            }

            binding.btnMore.setOnClickListener {
                // Handle more options
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerGalaxyTunnelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ServerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class DiffCallback : DiffUtil.ItemCallback<ServersCache>() {
        override fun areItemsTheSame(oldItem: ServersCache, newItem: ServersCache): Boolean {
            return oldItem.guid == newItem.guid
        }

        override fun areContentsTheSame(oldItem: ServersCache, newItem: ServersCache): Boolean {
            return oldItem == newItem
        }
    }
}

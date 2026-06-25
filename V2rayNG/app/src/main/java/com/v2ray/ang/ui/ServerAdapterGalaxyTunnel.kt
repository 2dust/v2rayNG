package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemServerGalaxyTunnelBinding
import com.v2ray.ang.dto.ServerConfig

class ServerAdapterGalaxyTunnel(
    private val onSelect: (ServerConfig) -> Unit,
    private val onEdit: (ServerConfig) -> Unit,
    private val onDelete: (ServerConfig) -> Unit,
    private val onTest: (ServerConfig) -> Unit
) : ListAdapter<ServerConfig, ServerAdapterGalaxyTunnel.ServerViewHolder>(DiffCallback()) {

    private var selectedPosition = -1

    inner class ServerViewHolder(
        val binding: ItemServerGalaxyTunnelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(server: ServerConfig, position: Int) {
            binding.tvServerName.text = server.remarks
            binding.tvServerProtocol.text = "${server.configType.protocolScheme} • ${server.outboundBean?.streamSettings?.network ?: "tcp"}"

            // Latency badge
            val latency = server.testResult ?: -1
            when {
                latency < 0 -> {
                    binding.tvLatency.text = "--"
                    binding.tvLatency.background = ContextCompat.getDrawable(itemView.context, R.drawable.gt_latency_badge_medium)
                }
                latency < 200 -> {
                    binding.tvLatency.text = "${latency}ms"
                    binding.tvLatency.background = ContextCompat.getDrawable(itemView.context, R.drawable.gt_latency_badge_good)
                }
                latency < 500 -> {
                    binding.tvLatency.text = "${latency}ms"
                    binding.tvLatency.background = ContextCompat.getDrawable(itemView.context, R.drawable.gt_latency_badge_medium)
                }
                else -> {
                    binding.tvLatency.text = "${latency}ms"
                    binding.tvLatency.background = ContextCompat.getDrawable(itemView.context, R.drawable.gt_latency_badge_bad)
                }
            }

            // Radio button
            binding.radioServer.isChecked = position == selectedPosition

            // Click handlers
            binding.root.setOnClickListener {
                val previousSelected = selectedPosition
                selectedPosition = position
                notifyItemChanged(previousSelected)
                notifyItemChanged(selectedPosition)
                onSelect(server)
            }

            binding.btnMore.setOnClickListener {
                // Show popup menu
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

    class DiffCallback : DiffUtil.ItemCallback<ServerConfig>() {
        override fun areItemsTheSame(oldItem: ServerConfig, newItem: ServerConfig): Boolean {
            return oldItem.guid == newItem.guid
        }

        override fun areContentsTheSame(oldItem: ServerConfig, newItem: ServerConfig): Boolean {
            return oldItem == newItem
        }
    }
}
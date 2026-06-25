package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R

/**
 * Galaxy Tunnel UI - Server List Adapter
 * Fixed version with correct view references
 */
class ServerAdapterGalaxyTunnel(
    private val onServerSelected: (ServersCache.ServerItem) -> Unit,
    private val onServerMoreClick: (ServersCache.ServerItem, View) -> Unit
) : ListAdapter<ServersCache.ServerItem, ServerAdapterGalaxyTunnel.ServerViewHolder>(ServerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server_galaxy_tunnel, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvServerName: TextView = itemView.findViewById(R.id.tvServerName)
        private val tvLatency: TextView = itemView.findViewById(R.id.tvLatency)
        private val radioServer: RadioButton = itemView.findViewById(R.id.radioServer)
        private val btnMore: View = itemView.findViewById(R.id.btnMore)

        fun bind(server: ServersCache.ServerItem) {
            tvServerName.text = server.remarks
            tvLatency.text = "${server.serverAddress}:${server.serverPort}"
            radioServer.isChecked = server.isActive

            itemView.setOnClickListener {
                onServerSelected(server)
            }

            btnMore.setOnClickListener { view ->
                onServerMoreClick(server, view)
            }
        }
    }

    class ServerDiffCallback : DiffUtil.ItemCallback<ServersCache.ServerItem>() {
        override fun areItemsTheSame(oldItem: ServersCache.ServerItem, newItem: ServersCache.ServerItem): Boolean {
            return oldItem.guid == newItem.guid
        }

        override fun areContentsTheSame(oldItem: ServersCache.ServerItem, newItem: ServersCache.ServerItem): Boolean {
            return oldItem == newItem
        }
    }
}

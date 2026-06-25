package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.v2ray.ang.R

/**
 * Galaxy Tunnel UI - Server List Adapter
 */
class GalaxyServerAdapter(
    private val onServerSelected: (ServersCache.ServerItem) -> Unit,
    private val onPingTest: (ServersCache.ServerItem) -> Unit
) : ListAdapter<ServersCache.ServerItem, GalaxyServerAdapter.ServerViewHolder>(ServerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_galaxy_server, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvServerName: TextView = itemView.findViewById(R.id.tvItemServerName)
        private val tvPing: TextView = itemView.findViewById(R.id.tvItemPing)
        private val progressPing: CircularProgressIndicator = itemView.findViewById(R.id.progressPing)
        private val indicatorActive: View = itemView.findViewById(R.id.indicatorActive)

        fun bind(server: ServersCache.ServerItem) {
            tvServerName.text = server.remarks
            tvServerName.setTextColor(
                ContextCompat.getColor(itemView.context,
                    if (server.isActive) R.color.text_primary else R.color.text_secondary
                )
            )

            // Active indicator
            indicatorActive.visibility = if (server.isActive) View.VISIBLE else View.INVISIBLE

            // Ping display
            tvPing.visibility = View.VISIBLE
            progressPing.visibility = View.GONE

            itemView.setOnClickListener {
                onServerSelected(server)
            }

            itemView.setOnLongClickListener {
                onPingTest(server)
                true
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

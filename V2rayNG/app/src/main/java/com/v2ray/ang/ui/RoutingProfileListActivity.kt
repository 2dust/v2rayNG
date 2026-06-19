package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityRoutingProfileListBinding
import com.v2ray.ang.dto.entities.RoutingProfile
import com.v2ray.ang.handler.SettingsManager

class RoutingProfileListActivity : BaseActivity() {

    private val binding by lazy { ActivityRoutingProfileListBinding.inflate(layoutInflater) }
    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.routing_profile_list_title))

        adapter = ProfileAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            showCreateProfileDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_routing_profile_list, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.add_routing_profile -> {
            showCreateProfileDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshData() {
        adapter.notifyDataSetChanged()
    }

    private fun showCreateProfileDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.routing_profile_edit_name)
        AlertDialog.Builder(this)
            .setTitle(R.string.routing_profile_add)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val profile = SettingsManager.createRoutingProfile(name)
                    startActivity(
                        Intent(this, RoutingSettingActivity::class.java)
                            .putExtra("profileId", profile.id)
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(profile: RoutingProfile) {
        val input = EditText(this)
        input.setText(profile.name)
        AlertDialog.Builder(this)
            .setTitle(R.string.routing_profile_edit_name)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    profile.name = name
                    SettingsManager.saveRoutingProfile(profile)
                    refreshData()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteProfile(profile: RoutingProfile) {
        val profiles = SettingsManager.getAllRoutingProfiles()
        if (profiles.size <= 1) {
            AlertDialog.Builder(this)
                .setMessage(R.string.routing_profile_cannot_delete_last)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.routing_profile_delete_confirm, profile.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                SettingsManager.removeRoutingProfileById(profile.id)
                refreshData()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    inner class ProfileAdapter : RecyclerView.Adapter<ProfileAdapter.ViewHolder>() {

        private val profiles get() = SettingsManager.getAllRoutingProfiles()
        private val activeId get() = SettingsManager.getActiveRoutingProfileId()

        override fun getItemCount() = profiles.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recycler_routing_profile, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val profile = profiles[position]
            val isActive = profile.id == activeId

            holder.tvName.text = profile.name
            holder.tvRulesCount.text = resources.getQuantityString(
                R.plurals.routing_profile_rules_count,
                profile.rulesets.size,
                profile.rulesets.size
            )
            holder.ivActive.visibility = if (isActive) View.VISIBLE else View.INVISIBLE

            // Tap → open profile (also sets it active)
            holder.itemView.setOnClickListener {
                startActivity(
                    Intent(this@RoutingProfileListActivity, RoutingSettingActivity::class.java)
                        .putExtra("profileId", profile.id)
                )
            }

            // Long press → options menu
            holder.itemView.setOnLongClickListener {
                showProfileOptions(profile, isActive)
                true
            }

            holder.ivActive.setOnClickListener {
                SettingsManager.setActiveRoutingProfile(profile.id)
                notifyDataSetChanged()
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_profile_name)
            val tvRulesCount: TextView = view.findViewById(R.id.tv_rules_count)
            val ivActive: ImageView = view.findViewById(R.id.iv_active)
        }
    }

    private fun showProfileOptions(profile: RoutingProfile, isActive: Boolean) {
        val options = mutableListOf<String>()
        if (!isActive) options.add(getString(R.string.routing_profile_set_active))
        options.add(getString(R.string.routing_profile_rename))
        options.add(getString(R.string.routing_profile_delete))

        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(options.toTypedArray()) { _, i ->
                var idx = i
                if (!isActive) {
                    if (idx == 0) {
                        SettingsManager.setActiveRoutingProfile(profile.id)
                        refreshData()
                        return@setItems
                    }
                    idx -= 1
                }
                when (idx) {
                    0 -> showRenameDialog(profile)
                    1 -> confirmDeleteProfile(profile)
                }
            }
            .show()
    }
}

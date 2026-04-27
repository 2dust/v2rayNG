package com.v2ray.ang.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.NetworkTrigger
import com.v2ray.ang.handler.MmkvManager

class NetworkTriggerActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TriggerAdapter
    private val triggers = mutableListOf<NetworkTrigger>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(R.layout.activity_network_trigger, showHomeAsUp = true, title = getString(R.string.pref_network_triggers_title))

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TriggerAdapter()
        recyclerView.adapter = adapter

        triggers.addAll(MmkvManager.decodeNetworkTriggers())
        adapter.notifyDataSetChanged()

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            showEditDialog(null, -1)
        }
    }

    private fun saveTriggers() {
        MmkvManager.encodeNetworkTriggers(triggers)
    }

    private fun showEditDialog(trigger: NetworkTrigger?, position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_network_trigger_edit, null)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinner_trigger_type)
        val layoutSsid = dialogView.findViewById<LinearLayout>(R.id.layout_ssid)
        val etSsid = dialogView.findViewById<EditText>(R.id.et_ssid)
        val spinnerAction = dialogView.findViewById<Spinner>(R.id.spinner_action)

        val typeLabels = arrayOf(
            getString(R.string.trigger_type_wifi_any),
            getString(R.string.trigger_type_mobile_data),
            getString(R.string.trigger_type_wifi_ssid)
        )
        val typeValues = arrayOf(
            AppConfig.TRIGGER_TYPE_WIFI_ANY,
            AppConfig.TRIGGER_TYPE_MOBILE_DATA,
            AppConfig.TRIGGER_TYPE_WIFI_SSID
        )
        spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeLabels)

        val actionLabels = arrayOf(
            getString(R.string.trigger_action_start),
            getString(R.string.trigger_action_stop)
        )
        val actionValues = arrayOf(AppConfig.TRIGGER_ACTION_START, AppConfig.TRIGGER_ACTION_STOP)
        spinnerAction.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, actionLabels)

        if (trigger != null) {
            val typeIdx = typeValues.indexOf(trigger.triggerType).coerceAtLeast(0)
            spinnerType.setSelection(typeIdx)
            etSsid.setText(trigger.targetSsid)
            val actionIdx = actionValues.indexOf(trigger.action).coerceAtLeast(0)
            spinnerAction.setSelection(actionIdx)
            layoutSsid.visibility = if (trigger.triggerType == AppConfig.TRIGGER_TYPE_WIFI_SSID) View.VISIBLE else View.GONE
        }

        spinnerType.post {
            spinnerType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    layoutSsid.visibility = if (typeValues[pos] == AppConfig.TRIGGER_TYPE_WIFI_SSID) View.VISIBLE else View.GONE
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (trigger == null) getString(R.string.title_add_network_trigger) else getString(R.string.title_edit_network_trigger))
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selectedType = typeValues[spinnerType.selectedItemPosition]
                val ssid = if (selectedType == AppConfig.TRIGGER_TYPE_WIFI_SSID) etSsid.text.toString().trim() else ""
                val selectedAction = actionValues[spinnerAction.selectedItemPosition]
                val newTrigger = NetworkTrigger(
                    enabled = trigger?.enabled ?: true,
                    triggerType = selectedType,
                    targetSsid = ssid,
                    action = selectedAction
                )
                if (position >= 0) {
                    triggers[position] = newTrigger
                    adapter.notifyItemChanged(position)
                } else {
                    triggers.add(newTrigger)
                    adapter.notifyItemInserted(triggers.size - 1)
                }
                saveTriggers()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    inner class TriggerAdapter : RecyclerView.Adapter<TriggerAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvType: TextView = view.findViewById(R.id.tv_trigger_type)
            val tvDetail: TextView = view.findViewById(R.id.tv_trigger_detail)
            val switchEnabled: Switch = view.findViewById(R.id.switch_enabled)
            val layoutEdit: LinearLayout = view.findViewById(R.id.layout_edit)
            val layoutRemove: LinearLayout = view.findViewById(R.id.layout_remove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recycler_network_trigger, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val trigger = triggers[position]

            holder.tvType.text = when (trigger.triggerType) {
                AppConfig.TRIGGER_TYPE_WIFI_ANY -> getString(R.string.trigger_type_wifi_any)
                AppConfig.TRIGGER_TYPE_MOBILE_DATA -> getString(R.string.trigger_type_mobile_data)
                AppConfig.TRIGGER_TYPE_WIFI_SSID -> getString(R.string.trigger_type_wifi_ssid)
                else -> trigger.triggerType
            }

            val actionStr = if (trigger.action == AppConfig.TRIGGER_ACTION_START)
                getString(R.string.trigger_action_start)
            else
                getString(R.string.trigger_action_stop)

            holder.tvDetail.text = if (trigger.triggerType == AppConfig.TRIGGER_TYPE_WIFI_SSID && trigger.targetSsid.isNotEmpty())
                "${trigger.targetSsid} → $actionStr"
            else
                "→ $actionStr"

            holder.switchEnabled.setOnCheckedChangeListener(null)
            holder.switchEnabled.isChecked = trigger.enabled
            holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                triggers[position] = trigger.copy(enabled = isChecked)
                saveTriggers()
            }

            holder.layoutEdit.setOnClickListener {
                showEditDialog(trigger, position)
            }

            holder.layoutRemove.setOnClickListener {
                triggers.removeAt(position)
                notifyItemRemoved(position)
                saveTriggers()
            }
        }

        override fun getItemCount() = triggers.size
    }
}

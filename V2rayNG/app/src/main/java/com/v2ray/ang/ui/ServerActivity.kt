package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.AppConfig.DEFAULT_PORT
import com.v2ray.ang.AppConfig.PREF_ALLOW_INSECURE
import com.v2ray.ang.AppConfig.REALITY
import com.v2ray.ang.AppConfig.TLS
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_ADDRESS_V6
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_MTU
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.NetworkType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

class ServerActivity : BaseActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.VMESS.value))
            ?: EConfigType.VMESS
    }
    private val subscriptionId by lazy {
        intent.getStringExtra("subscriptionId")
    }

    private val securitys: Array<out String> by lazy {
        resources.getStringArray(R.array.securitys)
    }
    private val shadowsocksSecuritys: Array<out String> by lazy {
        resources.getStringArray(R.array.ss_securitys)
    }
    private val flows: Array<out String> by lazy {
        resources.getStringArray(R.array.flows)
    }
    private val networks: Array<out String> by lazy {
        resources.getStringArray(R.array.networks)
    }
    private val tcpTypes: Array<out String> by lazy {
        resources.getStringArray(R.array.header_type_tcp)
    }
    private val kcpAndQuicTypes: Array<out String> by lazy {
        resources.getStringArray(R.array.header_type_kcp_and_quic)
    }
    private val grpcModes: Array<out String> by lazy {
        resources.getStringArray(R.array.mode_type_grpc)
    }
    private val streamSecuritys: Array<out String> by lazy {
        resources.getStringArray(R.array.streamsecurityxs)
    }
    private val allowinsecures: Array<out String> by lazy {
        resources.getStringArray(R.array.allowinsecures)
    }
    private val uTlsItems: Array<out String> by lazy {
        resources.getStringArray(R.array.streamsecurity_utls)
    }
    private val alpns: Array<out String> by lazy {
        resources.getStringArray(R.array.streamsecurity_alpn)
    }
    private val xhttpMode: Array<out String> by lazy {
        resources.getStringArray(R.array.xhttp_mode)
    }


    // Kotlin synthetics was used, but since it is removed in 1.8. We switch to old manual approach.
    // We don't use AndroidViewBinding because, it is better to share similar logics for different
    // protocols. Use findViewById manually ensures the xml are de-coupled with the activity logic.
    private val et_remarks: EditText by lazy { findViewById(R.id.et_remarks) }
    private val et_address: EditText by lazy { findViewById(R.id.et_address) }
    private val et_port: EditText by lazy { findViewById(R.id.et_port) }
    private val et_id: EditText by lazy { findViewById(R.id.et_id) }
    private val et_security: EditText? by lazy { findViewById(R.id.et_security) }
    private val sp_flow: Spinner? by lazy { findViewById(R.id.sp_flow) }
    private val sp_security: Spinner? by lazy { findViewById(R.id.sp_security) }
    private val sp_stream_security: Spinner? by lazy { findViewById(R.id.sp_stream_security) }
    private val sp_allow_insecure: Spinner? by lazy { findViewById(R.id.sp_allow_insecure) }
    private val container_allow_insecure: LinearLayout? by lazy { findViewById(R.id.lay_allow_insecure) }
    private val et_sni: EditText? by lazy { findViewById(R.id.et_sni) }
    private val container_sni: LinearLayout? by lazy { findViewById(R.id.lay_sni) }
    private val sp_stream_fingerprint: Spinner? by lazy { findViewById(R.id.sp_stream_fingerprint) } //uTLS
    private val container_fingerprint: LinearLayout? by lazy { findViewById(R.id.lay_stream_fingerprint) }
    private val sp_network: Spinner? by lazy { findViewById(R.id.sp_network) }
    private val sp_header_type: Spinner? by lazy { findViewById(R.id.sp_header_type) }
    private val sp_header_type_title: TextView? by lazy { findViewById(R.id.sp_header_type_title) }
    private val tv_request_host: TextView? by lazy { findViewById(R.id.tv_request_host) }
    private val et_request_host: EditText? by lazy { findViewById(R.id.et_request_host) }
    private val tv_path: TextView? by lazy { findViewById(R.id.tv_path) }
    private val et_path: EditText? by lazy { findViewById(R.id.et_path) }
    private val sp_stream_alpn: Spinner? by lazy { findViewById(R.id.sp_stream_alpn) } //uTLS
    private val container_alpn: LinearLayout? by lazy { findViewById(R.id.lay_stream_alpn) }
    private val et_public_key: EditText? by lazy { findViewById(R.id.et_public_key) }
    private val et_preshared_key: EditText? by lazy { findViewById(R.id.et_preshared_key) }
    private val container_public_key: LinearLayout? by lazy { findViewById(R.id.lay_public_key) }
    private val et_short_id: EditText? by lazy { findViewById(R.id.et_short_id) }
    private val container_short_id: LinearLayout? by lazy { findViewById(R.id.lay_short_id) }
    private val et_spider_x: EditText? by lazy { findViewById(R.id.et_spider_x) }
    private val container_spider_x: LinearLayout? by lazy { findViewById(R.id.lay_spider_x) }
    private val et_reserved1: EditText? by lazy { findViewById(R.id.et_reserved1) }
    private val et_local_address: EditText? by lazy { findViewById(R.id.et_local_address) }
    private val et_local_mtu: EditText? by lazy { findViewById(R.id.et_local_mtu) }
    private val et_obfs_password: EditText? by lazy { findViewById(R.id.et_obfs_password) }
    private val et_port_hop: EditText? by lazy { findViewById(R.id.et_port_hop) }
    private val et_port_hop_interval: EditText? by lazy { findViewById(R.id.et_port_hop_interval) }
    private val et_pinsha256: EditText? by lazy { findViewById(R.id.et_pinsha256) }
    private val et_extra: EditText? by lazy { findViewById(R.id.et_extra) }
    private val layout_extra: LinearLayout? by lazy { findViewById(R.id.layout_extra) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.title_server)

        val config = MmkvManager.decodeServerConfig(editGuid)
        when (config?.configType ?: createConfigType) {
            EConfigType.VMESS -> setContentView(R.layout.activity_server_vmess)
            EConfigType.CUSTOM -> return
            EConfigType.SHADOWSOCKS -> setContentView(R.layout.activity_server_shadowsocks)
            EConfigType.SOCKS -> setContentView(R.layout.activity_server_socks)
            EConfigType.HTTP -> setContentView(R.layout.activity_server_socks)
            EConfigType.VLESS -> setContentView(R.layout.activity_server_vless)
            EConfigType.TROJAN -> setContentView(R.layout.activity_server_trojan)
            EConfigType.WIREGUARD -> setContentView(R.layout.activity_server_wireguard)
            EConfigType.HYSTERIA2 -> setContentView(R.layout.activity_server_hysteria2)
        }
        sp_network?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                val types = transportTypes(networks[position])
                sp_header_type?.isEnabled = types.size > 1
                val adapter =
                    ArrayAdapter(this@ServerActivity, android.R.layout.simple_spinner_item, types)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                sp_header_type?.adapter = adapter
                sp_header_type_title?.text =
                    when (networks[position]) {
                        NetworkType.GRPC.type -> getString(R.string.server_lab_mode_type)
                        NetworkType.XHTTP.type -> getString(R.string.server_lab_xhttp_mode)
                        else -> getString(R.string.server_lab_head_type)
                    }.orEmpty()
                sp_header_type?.setSelection(
                    Utils.arrayFind(
                        types,
                        when (networks[position]) {
                            NetworkType.GRPC.type -> config?.mode
                            NetworkType.XHTTP.type -> config?.xhttpMode
                            else -> config?.headerType
                        }.orEmpty()
                    )
                )

                et_request_host?.text = Utils.getEditable(
                    when (networks[position]) {
                        //"quic" -> config?.quicSecurity
                        NetworkType.GRPC.type -> config?.authority
                        else -> config?.host
                    }.orEmpty()
                )
                et_path?.text = Utils.getEditable(
                    when (networks[position]) {
                        NetworkType.KCP.type -> config?.seed
                        //"quic" -> config?.quicKey
                        NetworkType.GRPC.type -> config?.serviceName
                        else -> config?.path
                    }.orEmpty()
                )

                tv_request_host?.text = Utils.getEditable(
                    getString(
                        when (networks[position]) {
                            NetworkType.TCP.type -> R.string.server_lab_request_host_http
                            NetworkType.WS.type -> R.string.server_lab_request_host_ws
                            NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_request_host_httpupgrade
                            NetworkType.XHTTP.type -> R.string.server_lab_request_host_xhttp
                            NetworkType.H2.type -> R.string.server_lab_request_host_h2
                            //"quic" -> R.string.server_lab_request_host_quic
                            NetworkType.GRPC.type -> R.string.server_lab_request_host_grpc
                            else -> R.string.server_lab_request_host
                        }
                    )
                )

                tv_path?.text = Utils.getEditable(
                    getString(
                        when (networks[position]) {
                            NetworkType.KCP.type -> R.string.server_lab_path_kcp
                            NetworkType.WS.type -> R.string.server_lab_path_ws
                            NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_path_httpupgrade
                            NetworkType.XHTTP.type -> R.string.server_lab_path_xhttp
                            NetworkType.H2.type -> R.string.server_lab_path_h2
                            //"quic" -> R.string.server_lab_path_quic
                            NetworkType.GRPC.type -> R.string.server_lab_path_grpc
                            else -> R.string.server_lab_path
                        }
                    )
                )
                et_extra?.text = Utils.getEditable(
                    when (networks[position]) {
                        NetworkType.XHTTP.type -> config?.xhttpExtra
                        else -> null
                    }.orEmpty()
                )

                layout_extra?.visibility =
                    when (networks[position]) {
                        NetworkType.XHTTP.type -> View.VISIBLE
                        else -> View.GONE
                    }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // do nothing
            }
        }
        sp_stream_security?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                val isBlank = streamSecuritys[position].isBlank()
                val isTLS = streamSecuritys[position] == TLS

                when {
                    // Case 1: Null or blank
                    isBlank -> {
                        listOf(
                            container_sni, container_fingerprint, container_alpn,
                            container_allow_insecure, container_public_key,
                            container_short_id, container_spider_x
                        ).forEach { it?.visibility = View.GONE }
                    }

                    // Case 2: TLS value
                    isTLS -> {
                        listOf(
                            container_sni,
                            container_fingerprint,
                            container_alpn
                        ).forEach { it?.visibility = View.VISIBLE }
                        container_allow_insecure?.visibility = View.VISIBLE
                        listOf(
                            container_public_key,
                            container_short_id,
                            container_spider_x
                        ).forEach { it?.visibility = View.GONE }
                    }

                    // Case 3: Other reality values
                    else -> {
                        listOf(container_sni, container_fingerprint).forEach {
                            it?.visibility = View.VISIBLE
                        }
                        container_alpn?.visibility = View.GONE
                        container_allow_insecure?.visibility = View.GONE
                        listOf(
                            container_public_key,
                            container_short_id,
                            container_spider_x
                        ).forEach { it?.visibility = View.VISIBLE }
                    }
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                // do nothing
            }
        }
        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    /**
     * binding selected server config
     */
    private fun bindingServer(config: ProfileItem): Boolean {

        et_remarks.text = Utils.getEditable(config.remarks)
        et_address.text = Utils.getEditable(config.server.orEmpty())
        et_port.text = Utils.getEditable(config.serverPort ?: DEFAULT_PORT.toString())
        et_id.text = Utils.getEditable(config.password.orEmpty())

        if (config.configType == EConfigType.SOCKS || config.configType == EConfigType.HTTP) {
            et_security?.text = Utils.getEditable(config.username.orEmpty())
        } else if (config.configType == EConfigType.VLESS) {
            et_security?.text = Utils.getEditable(config.method.orEmpty())
            val flow = Utils.arrayFind(flows, config.flow.orEmpty())
            if (flow >= 0) {
                sp_flow?.setSelection(flow)
            }
        } else if (config.configType == EConfigType.WIREGUARD) {
            et_id.text = Utils.getEditable(config.secretKey.orEmpty())
            et_public_key?.text = Utils.getEditable(config.publicKey.orEmpty())
            et_preshared_key?.visibility = View.VISIBLE
            et_preshared_key?.text = Utils.getEditable(config.preSharedKey.orEmpty())
            et_reserved1?.text = Utils.getEditable(config.reserved ?: "0,0,0")
            et_local_address?.text = Utils.getEditable(
                config.localAddress ?: "$WIREGUARD_LOCAL_ADDRESS_V4,$WIREGUARD_LOCAL_ADDRESS_V6"
            )
            et_local_mtu?.text = Utils.getEditable(config.mtu?.toString() ?: WIREGUARD_LOCAL_MTU)
        } else if (config.configType == EConfigType.HYSTERIA2) {
            et_obfs_password?.text = Utils.getEditable(config.obfsPassword)
            et_port_hop?.text = Utils.getEditable(config.portHopping)
            et_port_hop_interval?.text = Utils.getEditable(config.portHoppingInterval)
            et_pinsha256?.text = Utils.getEditable(config.pinSHA256)
        }
        val securityEncryptions =
            if (config.configType == EConfigType.SHADOWSOCKS) shadowsocksSecuritys else securitys
        val security = Utils.arrayFind(securityEncryptions, config.method.orEmpty())
        if (security >= 0) {
            sp_security?.setSelection(security)
        }

        val streamSecurity = Utils.arrayFind(streamSecuritys, config.security.orEmpty())
        if (streamSecurity >= 0) {
            sp_stream_security?.setSelection(streamSecurity)
            container_sni?.visibility = View.VISIBLE
            container_fingerprint?.visibility = View.VISIBLE
            container_alpn?.visibility = View.VISIBLE

            et_sni?.text = Utils.getEditable(config.sni)
            config.fingerPrint?.let {
                val utlsIndex = Utils.arrayFind(uTlsItems, it)
                sp_stream_fingerprint?.setSelection(utlsIndex)
            }
            config.alpn?.let {
                val alpnIndex = Utils.arrayFind(alpns, it)
                sp_stream_alpn?.setSelection(alpnIndex)
            }
            if (config.security == TLS) {
                container_allow_insecure?.visibility = View.VISIBLE
                val allowinsecure = Utils.arrayFind(allowinsecures, config.insecure.toString())
                if (allowinsecure >= 0) {
                    sp_allow_insecure?.setSelection(allowinsecure)
                }
                container_public_key?.visibility = View.GONE
                container_short_id?.visibility = View.GONE
                container_spider_x?.visibility = View.GONE
            } else if (config.security == REALITY) {
                container_public_key?.visibility = View.VISIBLE
                et_public_key?.text = Utils.getEditable(config.publicKey.orEmpty())
                container_short_id?.visibility = View.VISIBLE
                et_short_id?.text = Utils.getEditable(config.shortId.orEmpty())
                container_spider_x?.visibility = View.VISIBLE
                et_spider_x?.text = Utils.getEditable(config.spiderX.orEmpty())
                container_allow_insecure?.visibility = View.GONE
            }
        }

        if (config.security.isNullOrEmpty()) {
            container_sni?.visibility = View.GONE
            container_fingerprint?.visibility = View.GONE
            container_alpn?.visibility = View.GONE
            container_allow_insecure?.visibility = View.GONE
            container_public_key?.visibility = View.GONE
            container_short_id?.visibility = View.GONE
            container_spider_x?.visibility = View.GONE
        }
        val network = Utils.arrayFind(networks, config.network.orEmpty())
        if (network >= 0) {
            sp_network?.setSelection(network)
        }
        return true
    }

    /**
     * clear or init server config
     */
    private fun clearServer(): Boolean {
        et_remarks.text = null
        et_address.text = null
        et_port.text = Utils.getEditable(DEFAULT_PORT.toString())
        et_id.text = null
        sp_security?.setSelection(0)
        sp_network?.setSelection(0)

        sp_header_type?.setSelection(0)
        et_request_host?.text = null
        et_path?.text = null
        sp_stream_security?.setSelection(0)
        sp_allow_insecure?.setSelection(0)
        et_sni?.text = null

        //et_security.text = null
        sp_flow?.setSelection(0)
        et_public_key?.text = null
        et_reserved1?.text = Utils.getEditable("0,0,0")
        et_local_address?.text =
            Utils.getEditable("${WIREGUARD_LOCAL_ADDRESS_V4},${WIREGUARD_LOCAL_ADDRESS_V6}")
        et_local_mtu?.text = Utils.getEditable(WIREGUARD_LOCAL_MTU)
        return true
    }

    /**
     * save server config
     */
    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(et_remarks.text.toString())) {
            toast(R.string.server_lab_remarks)
            return false
        }
        if (TextUtils.isEmpty(et_address.text.toString())) {
            toast(R.string.server_lab_address)
            return false
        }
        if (createConfigType != EConfigType.HYSTERIA2) {
            if (Utils.parseInt(et_port.text.toString()) <= 0) {
                toast(R.string.server_lab_port)
                return false
            }
        }
        val config =
            MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(createConfigType)
        if (config.configType != EConfigType.SOCKS
            && config.configType != EConfigType.HTTP
            && TextUtils.isEmpty(et_id.text.toString())
        ) {
            if (config.configType == EConfigType.TROJAN
                || config.configType == EConfigType.SHADOWSOCKS
                || config.configType == EConfigType.HYSTERIA2
            ) {
                toast(R.string.server_lab_id3)
            } else {
                toast(R.string.server_lab_id)
            }
            return false
        }
        sp_stream_security?.let {
            if (config.configType == EConfigType.TROJAN && TextUtils.isEmpty(streamSecuritys[it.selectedItemPosition])) {
                toast(R.string.server_lab_stream_security)
                return false
            }
        }
        if (et_extra?.text?.toString().isNotNullEmpty()) {
            if (JsonUtil.parseString(et_extra?.text?.toString()) == null) {
                toast(R.string.server_lab_xhttp_extra)
                return false
            }
        }

        saveCommon(config)
        saveStreamSettings(config)
        saveTls(config)

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        Log.d(ANG_PACKAGE, JsonUtil.toJsonPretty(config) ?: "")
        MmkvManager.encodeServerConfig(editGuid, config)
        toast(R.string.toast_success)
        finish()
        return true
    }

    private fun saveCommon(config: ProfileItem) {
        config.remarks = et_remarks.text.toString().trim()
        config.server = et_address.text.toString().trim()
        config.serverPort = et_port.text.toString().trim()
        config.password = et_id.text.toString().trim()

        if (config.configType == EConfigType.VMESS) {
            config.method = securitys[sp_security?.selectedItemPosition ?: 0]
        } else if (config.configType == EConfigType.VLESS) {
            config.method = et_security?.text.toString().trim()
            config.flow = flows[sp_flow?.selectedItemPosition ?: 0]
        } else if (config.configType == EConfigType.SHADOWSOCKS) {
            config.method = shadowsocksSecuritys[sp_security?.selectedItemPosition ?: 0]
        } else if (config.configType == EConfigType.SOCKS || config.configType == EConfigType.HTTP) {
            if (!TextUtils.isEmpty(et_security?.text) || !TextUtils.isEmpty(et_id.text)) {
                config.username = et_security?.text.toString().trim()
            }
        } else if (config.configType == EConfigType.TROJAN) {
        } else if (config.configType == EConfigType.WIREGUARD) {
            config.secretKey = et_id.text.toString().trim()
            config.publicKey = et_public_key?.text.toString().trim()
            config.preSharedKey = et_preshared_key?.text.toString().trim()
            config.reserved = et_reserved1?.text.toString().trim()
            config.localAddress = et_local_address?.text.toString().trim()
            config.mtu = Utils.parseInt(et_local_mtu?.text.toString())
        } else if (config.configType == EConfigType.HYSTERIA2) {
            config.obfsPassword = et_obfs_password?.text?.toString()
            config.portHopping = et_port_hop?.text?.toString()
            config.portHoppingInterval = et_port_hop_interval?.text?.toString()
            config.pinSHA256 = et_pinsha256?.text?.toString()
        }
    }


    private fun saveStreamSettings(profileItem: ProfileItem) {
        val network = sp_network?.selectedItemPosition ?: return
        val type = sp_header_type?.selectedItemPosition ?: return
        val requestHost = et_request_host?.text?.toString()?.trim() ?: return
        val path = et_path?.text?.toString()?.trim() ?: return

        profileItem.network = networks[network]
        profileItem.headerType = transportTypes(networks[network])[type]
        profileItem.host = requestHost
        profileItem.path = path
        profileItem.seed = path
        profileItem.quicSecurity = requestHost
        profileItem.quicKey = path
        profileItem.mode = transportTypes(networks[network])[type]
        profileItem.serviceName = path
        profileItem.authority = requestHost
        profileItem.xhttpMode = transportTypes(networks[network])[type]
        profileItem.xhttpExtra = et_extra?.text?.toString()?.trim()
    }

    private fun saveTls(config: ProfileItem) {
        val streamSecurity = sp_stream_security?.selectedItemPosition ?: return
        val sniField = et_sni?.text?.toString()?.trim()
        val allowInsecureField = sp_allow_insecure?.selectedItemPosition
        val utlsIndex = sp_stream_fingerprint?.selectedItemPosition ?: 0
        val alpnIndex = sp_stream_alpn?.selectedItemPosition ?: 0
        val publicKey = et_public_key?.text?.toString()
        val shortId = et_short_id?.text?.toString()
        val spiderX = et_spider_x?.text?.toString()

        val allowInsecure =
            if (allowInsecureField == null || allowinsecures[allowInsecureField].isBlank()) {
                MmkvManager.decodeSettingsBool(PREF_ALLOW_INSECURE)
            } else {
                allowinsecures[allowInsecureField].toBoolean()
            }

        config.security = streamSecuritys[streamSecurity]
        config.insecure = allowInsecure
        config.sni = sniField
        config.fingerPrint = uTlsItems[utlsIndex]
        config.alpn = alpns[alpnIndex]
        config.publicKey = publicKey
        config.shortId = shortId
        config.spiderX = spiderX
    }

    private fun transportTypes(network: String?): Array<out String> {
        return when (network) {
            NetworkType.TCP.type -> {
                tcpTypes
            }

            NetworkType.KCP.type -> {
                kcpAndQuicTypes
            }

            NetworkType.GRPC.type -> {
                grpcModes
            }

            NetworkType.XHTTP.type -> {
                xhttpMode
            }

            else -> {
                arrayOf("---")
            }
        }
    }

    /**
     * delete server config
     */
    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            if (editGuid != MmkvManager.getSelectServer()) {
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE) == true) {
                    AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            MmkvManager.removeServer(editGuid)
                            finish()
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            // do nothing
                        }
                        .show()
                } else {
                    MmkvManager.removeServer(editGuid)
                    finish()
                }
            } else {
                application.toast(R.string.toast_action_not_allowed)
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delButton = menu.findItem(R.id.del_config)
        val saveButton = menu.findItem(R.id.save_config)

        if (editGuid.isNotEmpty()) {
            if (isRunning) {
                delButton?.isVisible = false
                saveButton?.isVisible = false
            }
        } else {
            delButton?.isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            deleteServer()
            true
        }

        R.id.save_config -> {
            saveServer()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }
}

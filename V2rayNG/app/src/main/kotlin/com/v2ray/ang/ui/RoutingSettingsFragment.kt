package com.v2ray.ang.ui

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import com.v2ray.ang.R
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.util.Utils
import kotlinx.android.synthetic.main.fragment_routing_settings.*
import android.view.MenuInflater
import com.tbruyelle.rxpermissions.RxPermissions
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URL

class RoutingSettingsFragment : Fragment() {
    companion object {
        private const val routing_arg = "routing_arg"
        private const val REQUEST_SCAN_REPLACE = 11
        private const val REQUEST_SCAN_APPEND = 12
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_routing_settings, container, false)
    }

    fun newInstance(arg: String): Fragment {
        val fragment = RoutingSettingsFragment()
        val bundle = Bundle()
        bundle.putString(routing_arg, arg)
        fragment.arguments = bundle
        return fragment
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val content = activity?.defaultDPreference?.getPrefString(arguments!!.getString(routing_arg), "")
        et_routing_content.text = Utils.getEditable(content!!)

        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_routing, menu)
        return super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.save_routing -> {
            val content = et_routing_content.text.toString()
            activity?.defaultDPreference?.setPrefString(arguments!!.getString(routing_arg), content)
            activity?.toast(R.string.toast_success)
            true
        }
        R.id.del_routing -> {
            et_routing_content.text = null
            true
        }
        R.id.scan_replace -> {
            scanQRcode(REQUEST_SCAN_REPLACE)
            true
        }
        R.id.scan_append -> {
            scanQRcode(REQUEST_SCAN_APPEND)
            true
        }
        R.id.default_rules -> {
            setDefaultRules()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    fun scanQRcode(requestCode: Int): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(activity!!)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        startActivityForResult(Intent(activity, ScannerActivity::class.java), requestCode)
                    else
                        activity?.toast(R.string.toast_permission_denied)
                }
//        }
        return true
    }

    fun setDefaultRules(): Boolean {
        var url = AppConfig.v2rayCustomRoutingListUrl
        when (arguments!!.getString(routing_arg)) {
            AppConfig.PREF_V2RAY_ROUTING_AGENT -> {
                url += AppConfig.TAG_AGENT
            }
            AppConfig.PREF_V2RAY_ROUTING_DIRECT -> {
                url += AppConfig.TAG_DIRECT
            }
            AppConfig.PREF_V2RAY_ROUTING_BLOCKED -> {
                url += AppConfig.TAG_BLOCKED
            }
        }

        activity?.toast(R.string.msg_downloading_content)
        GlobalScope.launch(Dispatchers.IO) {
            val content = try {
                URL(url).readText()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
            launch(Dispatchers.Main) {
                et_routing_content.text = Utils.getEditable(content)
                activity?.toast(R.string.toast_success)
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_SCAN_REPLACE ->
                if (resultCode == RESULT_OK) {
                    val content = data?.getStringExtra("SCAN_RESULT")
                    et_routing_content.text = Utils.getEditable(content!!)
                }
            REQUEST_SCAN_APPEND ->
                if (resultCode == RESULT_OK) {
                    val content = data?.getStringExtra("SCAN_RESULT")
                    et_routing_content.text = Utils.getEditable("${et_routing_content.text},$content")
                }
        }
    }


}

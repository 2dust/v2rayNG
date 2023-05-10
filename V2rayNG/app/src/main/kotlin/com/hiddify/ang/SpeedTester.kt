//package com.hiddify.ang
//
//import android.util.Log
//import android.view.View
//import android.widget.Toast
//import com.google.android.material.dialog.MaterialAlertDialogBuilder
//import com.hiddify.ang.speedtest.GetSpeedTestHostsHandler
//import com.v2ray.ang.R
//import com.v2ray.ang.databinding.DialogSpeedtestBinding
//import com.v2ray.ang.extension.toast
//import com.v2ray.ang.service.V2RayServiceManager
//import com.v2ray.ang.ui.bottomsheets.SettingBottomSheets
//import fr.bmartel.speedtest.SpeedTestReport
//import fr.bmartel.speedtest.SpeedTestSocket
//import fr.bmartel.speedtest.inter.ISpeedTestListener
//import fr.bmartel.speedtest.model.SpeedTestError
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import java.util.concurrent.Executor
//import java.util.concurrent.Executors
//
//
//class SpeedTester {
//
//    private val executor: Executor = Executors.newSingleThreadExecutor()
//    fun executeAsync(speedTestSocket:SpeedTestSocket,download:Boolean,useProxy:Boolean) {
//        executor.execute {
//            if(useProxy)
//                speedTestSocket.setProxyServer("http://127.0.0.1:10809");
//            speedTestSocket.downloadSetupTime = 5000;
//            speedTestSocket.uploadSetupTime = 5000;
//            speedTestSocket.forceStopTask()
//
//            if (download)
//                speedTestSocket.startFixedDownload("https://bouygues.testdebit.info/100M.iso",30000);
//            else
//                speedTestSocket.startFixedUpload("https://bouygues.testdebit.info/", 100000000, 30000);
//        }
//    }
//    companion object {
//        var position = 0
//        var lastPosition = 0
//        var getSpeedTestHostsHandler: GetSpeedTestHostsHandler? = null
//        var tempBlackList: HashSet<String>? = null
//    public fun showSpeedTestDialog(inflater: SettingBottomSheets){
//
//        val activity=inflater.requireActivity()
//
//        MaterialAlertDialogBuilder(activity, R.style.AppTheme_ThemeOverlay_MaterialComponents_MaterialAlertDialog)
//            .setTitle(R.string.title_speedtest_proxy_select)
//            .setItems(R.array.speedtest_proxy_select) { dialog, which_proxy ->
//                dialog.dismiss()
//                MaterialAlertDialogBuilder(activity, R.style.AppTheme_ThemeOverlay_MaterialComponents_MaterialAlertDialog)
//                    .setTitle(R.string.title_speedtest_mode_select)
//                    .setItems(R.array.speedtest_mode_select) { dialog, which_mode ->
//                        dialog.dismiss()
//
//                        val useProxy=which_proxy==0
//                        if(useProxy)
//                            V2RayServiceManager.startV2Ray(activity)
//                        val tester=SpeedTester()
//                        val speedTestSocket = SpeedTestSocket();
//                        var isUpload=which_mode!=1
//
//                        val proxy_mode = activity.resources.getStringArray(R.array.speedtest_proxy_select)[which_proxy]
//                        val binding=DialogSpeedtestBinding.inflate(activity.layoutInflater)
//                        if (which_mode==0)binding.gagueDownload.visibility= View.GONE
//                        else if(which_mode==1)binding.gagueUpload.visibility= View.GONE
//                        var maxProgress=12f
//                        val mdialog=MaterialAlertDialogBuilder(activity, R.style.AppTheme_ThemeOverlay_MaterialComponents_MaterialAlertDialog)
//                            .setTitle(proxy_mode)
//                            .setView(binding.root)
//                            .setOnCancelListener{
//                                speedTestSocket.forceStopTask()
//                                it.dismiss()
//                            }
//                            val dialog=mdialog.show()
//
//                        speedTestSocket.addSpeedTestListener(object: ISpeedTestListener {
//                            override fun onCompletion(report: SpeedTestReport?){
//
//                                if (isUpload && which_mode==2){
//                                    CoroutineScope(Dispatchers.Main).launch {
//                                        binding.gagueDownload.prepareGauge(activity)
//                                        Toast.makeText(activity,R.string.toast_success, Toast.LENGTH_SHORT).show()
//                                    }
//                                    isUpload=false
//                                    tester.executeAsync(speedTestSocket, download = true,useProxy=useProxy)
//                                }else{
//                                    mdialog.setPositiveButton(R.string.tasker_setting_confirm) { dialog, which -> dialog.dismiss() }
//                                }
//
//
//                            }
//                            override fun onProgress(percent: Float, report: SpeedTestReport?){
//                                if (report == null) return
//                                activity.runOnUiThread {
//                                    CoroutineScope(Dispatchers.Main).launch {
//
//                                        val current =
//                                            report!!.transferRateBit.toFloat() / 1024f / 1024f
//                                        while (current > maxProgress) {
//                                            maxProgress += 12
//                                        }
//                                        if (isUpload) {
//                                            binding.gagueUpload.setProgress(current)
//                                            binding.gagueUpload.setMaxProgress(maxProgress)
//                                        } else {
//                                            binding.gagueDownload.setProgress(current)
//                                            binding.gagueDownload.setMaxProgress(maxProgress)
//                                        }
//                                    }
//                                }
//                            }
//                            override fun onError(speedTestError: SpeedTestError?, errorMessage: String?){
//                                activity.runOnUiThread  {
//                                    dialog.dismiss()
//                                    Log.e("SpeedTest",speedTestError.toString())
//                                    Log.e("SpeedTest",errorMessage?:"")
//                                    activity.toast(activity.getString(R.string.connection_test_error,errorMessage), Toast.LENGTH_LONG)
//                                }
//                            }
//                        })
//                        if (isUpload)
//                            binding.gagueUpload.prepareGauge(activity)
//                        else
//                            binding.gagueDownload.prepareGauge(activity)
//                        tester.executeAsync(speedTestSocket, download = !isUpload,useProxy=useProxy)
//
//
//
//
//
//                    }.show()
//            }
//            .show()
//    }
//
//
//    }
//}
//

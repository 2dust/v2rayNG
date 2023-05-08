package com.hiddify.ang.speedtest

import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.service.V2RayServiceManager
import org.achartengine.GraphicalView
import org.achartengine.model.XYMultipleSeriesDataset
import org.achartengine.renderer.XYMultipleSeriesRenderer
import org.achartengine.renderer.XYSeriesRenderer
import org.achartengine.ChartFactory;
import org.achartengine.model.XYSeries
import org.achartengine.renderer.XYSeriesRenderer.FillOutsideLine
import java.text.DecimalFormat

class SpeedTestActivity : AppCompatActivity() {
    var getSpeedTestHostsHandler: GetSpeedTestHostsHandler? = null
    var tempBlackList: HashSet<String>? = null
    var useProxy=false
    var testMode=3;
    public override fun onResume() {
        super.onResume()

        getSpeedTestHostsHandler = GetSpeedTestHostsHandler()
        getSpeedTestHostsHandler!!.start()
        selectMode()
    }


    fun selectMode(){
        var activity = this;
        MaterialAlertDialogBuilder(activity, R.style.AppTheme_ThemeOverlay_MaterialComponents_MaterialAlertDialog)
            .setTitle(R.string.title_speedtest_proxy_select)
            .setItems(R.array.speedtest_proxy_select) { dialog, which_proxy ->
                dialog.dismiss()
                useProxy = which_proxy == 0
                if (useProxy)
                    V2RayServiceManager.startV2Ray(activity)
                val proxymode = findViewById<View>(R.id.proxymode) as TextView
                proxymode.text=activity.resources.getStringArray(R.array.speedtest_proxy_select)[which_proxy]

                MaterialAlertDialogBuilder(activity, R.style.AppTheme_ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                    .setTitle(R.string.title_speedtest_mode_select)
                    .setItems(R.array.speedtest_mode_select) { dialog, which_mode ->
                        dialog.dismiss()
                        testMode=which_mode
                        startTest()
                    }.show()
            }.show()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speedtest)
        val startButton = findViewById<View>(R.id.startButton) as Button
        startButton.text = "Begin Test"

        getSpeedTestHostsHandler = GetSpeedTestHostsHandler()
        getSpeedTestHostsHandler!!.start()
        startButton.setOnClickListener{
            startButton.isEnabled = false
            selectMode()
        }

    }
        fun startTest(){
            val dec = DecimalFormat("#.##")
            tempBlackList = HashSet()
            val startButton = findViewById<View>(R.id.startButton) as Button
            startButton.isEnabled = false

            //Restart test icin eger baglanti koparsa
            if (getSpeedTestHostsHandler == null) {
                getSpeedTestHostsHandler = GetSpeedTestHostsHandler()
                getSpeedTestHostsHandler!!.start()
            }
            Thread(object : Runnable {

                var rotate: RotateAnimation? = null
                var barImageView =
                    findViewById<View>(R.id.barImageView) as ImageView
                var pingTextView = findViewById<View>(R.id.pingTextView) as TextView
                var downloadTextView =
                    findViewById<View>(R.id.downloadTextView) as TextView
                var uploadTextView =
                    findViewById<View>(R.id.uploadTextView) as TextView

                override fun run() {
                    runOnUiThread {
                        startButton.text = "Selecting best server based on ping..."
                    }
                    if (useProxy)
                        Thread.sleep(3000)
                    //Get egcodes.speedtest hosts
                    var timeCount = 600 //1min
                    while (!getSpeedTestHostsHandler!!.isFinished) {
                        timeCount--
                        try {
                            Thread.sleep(100)
                        } catch (e: InterruptedException) {
                        }
                        if (timeCount <= 0) {
                            runOnUiThread {
                                Toast.makeText(
                                    applicationContext,
                                    "No Connection...",
                                    Toast.LENGTH_LONG
                                ).show()
                                startButton.isEnabled = true
                                startButton.textSize = 16f
                                startButton.text = "Restart Test"
                            }
                            getSpeedTestHostsHandler = null
                            return
                        }
                    }

                    //Find closest server
                    val mapKey = getSpeedTestHostsHandler!!.getMapKey()
                    val mapValue = getSpeedTestHostsHandler!!.getMapValue()
                    val selfLat = getSpeedTestHostsHandler!!.getSelfLat()
                    val selfLon = getSpeedTestHostsHandler!!.getSelfLon()
                    var tmp = 19349458.0
                    var dist = 0.0
                    var findServerIndex = 0
                    for (index in mapKey.keys) {
                        if (tempBlackList!!.contains(mapValue[index]!![5])) {
                            continue
                        }
                        val source = Location("Source")
                        source.latitude = selfLat
                        source.longitude = selfLon
                        val ls = mapValue[index]!!
                        val dest = Location("Dest")
                        dest.latitude = ls[0].toDouble()
                        dest.longitude = ls[1].toDouble()
                        val distance = source.distanceTo(dest).toDouble()
                        if (tmp > distance) {
                            tmp = distance
                            dist = distance
                            findServerIndex = index
                        }
                    }
                    val testAddr = mapKey[findServerIndex]!!
                        .replace("http://", "https://")
                    val info = mapValue[findServerIndex]
                    val distance = dist
                    if (info == null) {
                        runOnUiThread {
                            startButton.textSize = 12f
                            startButton.text =
                                "There was a problem in getting Host Location. Try again later."
                        }
                        return
                    }
                    runOnUiThread {
                        startButton.textSize = 13f
                        startButton.text = String.format(
                            "Host Location: %s [Distance: %s km]",
                            info[2],
                            DecimalFormat("#.##").format(distance / 1000)
                        )
                    }

                    //Init Ping graphic
                    val chartPing = findViewById<View>(R.id.chartPing) as LinearLayout
                    val pingRenderer = XYSeriesRenderer()
                    val pingFill: XYSeriesRenderer.FillOutsideLine =
                        FillOutsideLine(XYSeriesRenderer.FillOutsideLine.Type.BOUNDS_ALL)
                    pingFill.setColor(Color.parseColor("#4d5a6a"))
                    pingRenderer.addFillOutsideLine(pingFill)
                    pingRenderer.setDisplayChartValues(false)
                    pingRenderer.setShowLegendItem(false)
                    pingRenderer.setColor(Color.parseColor("#4d5a6a"))
                    pingRenderer.setLineWidth(5f)
                    val multiPingRenderer = XYMultipleSeriesRenderer()
                    multiPingRenderer.setXLabels(0)
                    multiPingRenderer.setYLabels(0)
                    multiPingRenderer.setZoomEnabled(false)
                    multiPingRenderer.setXAxisColor(Color.parseColor("#647488"))
                    multiPingRenderer.setYAxisColor(Color.parseColor("#2F3C4C"))
                    multiPingRenderer.setPanEnabled(true, true)
                    multiPingRenderer.setZoomButtonsVisible(false)
                    multiPingRenderer.setMarginsColor(
                        Color.argb(
                            0x00,
                            0xff,
                            0x00,
                            0x00
                        )
                    )
                    multiPingRenderer.addSeriesRenderer(pingRenderer)

                    //Init Download graphic
                    val chartDownload =
                        findViewById<View>(R.id.chartDownload) as LinearLayout
                    val downloadRenderer = XYSeriesRenderer()
                    val downloadFill: XYSeriesRenderer.FillOutsideLine =
                        FillOutsideLine(XYSeriesRenderer.FillOutsideLine.Type.BOUNDS_ALL)
                    downloadFill.setColor(Color.parseColor("#4d5a6a"))
                    downloadRenderer.addFillOutsideLine(downloadFill)
                    downloadRenderer.setDisplayChartValues(false)
                    downloadRenderer.setColor(Color.parseColor("#4d5a6a"))
                    downloadRenderer.setShowLegendItem(false)
                    downloadRenderer.setLineWidth(5f)
                    val multiDownloadRenderer = XYMultipleSeriesRenderer()
                    multiDownloadRenderer.setXLabels(0)
                    multiDownloadRenderer.setYLabels(0)
                    multiDownloadRenderer.setZoomEnabled(false)
                    multiDownloadRenderer.setXAxisColor(Color.parseColor("#647488"))
                    multiDownloadRenderer.setYAxisColor(Color.parseColor("#2F3C4C"))
                    multiDownloadRenderer.setPanEnabled(false, false)
                    multiDownloadRenderer.setZoomButtonsVisible(false)
                    multiDownloadRenderer.setMarginsColor(
                        Color.argb(
                            0x00,
                            0xff,
                            0x00,
                            0x00
                        )
                    )
                    multiDownloadRenderer.addSeriesRenderer(downloadRenderer)

                    //Init Upload graphic
                    val chartUpload =
                        findViewById<View>(R.id.chartUpload) as LinearLayout
                    val uploadRenderer = XYSeriesRenderer()
                    val uploadFill: XYSeriesRenderer.FillOutsideLine =
                        FillOutsideLine(XYSeriesRenderer.FillOutsideLine.Type.BOUNDS_ALL)
                    uploadFill.setColor(Color.parseColor("#4d5a6a"))
                    uploadRenderer.addFillOutsideLine(uploadFill)
                    uploadRenderer.setDisplayChartValues(false)
                    uploadRenderer.setColor(Color.parseColor("#4d5a6a"))
                    uploadRenderer.setShowLegendItem(false)
                    uploadRenderer.setLineWidth(5f)
                    val multiUploadRenderer = XYMultipleSeriesRenderer()
                    multiUploadRenderer.setXLabels(0)
                    multiUploadRenderer.setYLabels(0)
                    multiUploadRenderer.setZoomEnabled(false)
                    multiUploadRenderer.setXAxisColor(Color.parseColor("#647488"))
                    multiUploadRenderer.setYAxisColor(Color.parseColor("#2F3C4C"))
                    multiUploadRenderer.setPanEnabled(false, false)
                    multiUploadRenderer.setZoomButtonsVisible(false)
                    multiUploadRenderer.setMarginsColor(
                        Color.argb(
                            0x00,
                            0xff,
                            0x00,
                            0x00
                        )
                    )
                    multiUploadRenderer.addSeriesRenderer(uploadRenderer)

                    //Reset value, graphics
                    runOnUiThread {
                        pingTextView.text = "0 ms"
                        chartPing.removeAllViews()
                        downloadTextView.text = "0 Mbps"
                        chartDownload.removeAllViews()
                        uploadTextView.text = "0 Mbps"
                        chartUpload.removeAllViews()
                    }
                    val pingRateList: MutableList<Double> =
                        ArrayList()
                    val downloadRateList: MutableList<Double> =
                        ArrayList()
                    val uploadRateList: MutableList<Double> =
                        ArrayList()
                    var pingTestStarted = false
                    var pingTestFinished = false
                    var downloadTestStarted = false
                    var downloadTestFinished = false
                    var uploadTestStarted = false
                    var uploadTestFinished = false


                    if(testMode==0){
                        downloadTestStarted=true
                        downloadTestFinished=true
                    }
                    if(testMode==1){
                        uploadTestStarted=true
                        uploadTestFinished=true
                    }

                    //Init Test
                    //val pingTest = PingTest(info[6].replace(":8080", ""), 3,useProxy=useProxy)
                    val pingTest = PingTest(info[6], 10,useProxy=useProxy)
                    val downloadTest = HttpDownloadTest(
                        testAddr.replace(
                            testAddr.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()[testAddr.split("/".toRegex())
                                .dropLastWhile { it.isEmpty() }
                                .toTypedArray().size - 1], ""),useProxy)
                    val uploadTest = HttpUploadTest(testAddr,useProxy)


                    //Tests
                    while (true) {
                        if (!pingTestStarted) {
                            pingTest.start()
                            pingTestStarted = true
                        }
                        if (pingTestFinished && !downloadTestStarted) {
                            downloadTest.start()
                            downloadTestStarted = true
                        }
                        if (downloadTestFinished && !uploadTestStarted) {
                            uploadTest.start()
                            uploadTestStarted = true
                        }


                        //Ping Test
                        if (pingTestFinished) {
                            //Failure
                            if (pingTest.avgRtt == 0.0) {
                                println("Ping error...")
                            } else {
                                //Success
                                runOnUiThread {
                                    pingTextView.text = dec.format(pingTest.avgRtt) + " ms"
                                }
                            }
                        } else {
                            pingRateList.add(pingTest.instantRtt)
                            runOnUiThread {
                                pingTextView.text = dec.format(pingTest.instantRtt) + " ms"
                            }

                            //Update chart
                            runOnUiThread { // Creating an  XYSeries for Income
                                val pingSeries = XYSeries("")
                                pingSeries.setTitle("")
                                var count = 0.0
                                val tmpLs: List<Double> =
                                    ArrayList(pingRateList)
                                for (`val` in tmpLs) {

                                    pingSeries.add(count++, `val`)

                                }
                                val dataset = XYMultipleSeriesDataset()
                                dataset.addSeries(pingSeries)
                                val chartView: GraphicalView = ChartFactory.getLineChartView(
                                    baseContext, dataset, multiPingRenderer
                                )
                                chartPing.addView(chartView, 0)
                            }
                        }


                        //Download Test
                        if (pingTestFinished) {
                            if (downloadTestFinished ) {
                                //Failure
                                if (downloadTest.getFinalDownloadRate() == 0.0) {
                                    println("Download error...")
                                } else {
                                    //Success
                                    runOnUiThread {
                                        downloadTextView.text =
                                            dec.format(downloadTest.getFinalDownloadRate()) + " Mbps"
                                    }
                                }
                            } else {
                                //Calc position
                                val downloadRate = downloadTest.getInstantDownloadRate()
                                downloadRateList.add(downloadRate)
                                position = getPositionByRate(downloadRate)
                                runOnUiThread {
                                    rotate = RotateAnimation(
                                        lastPosition.toFloat(),
                                        position.toFloat(),
                                        Animation.RELATIVE_TO_SELF,
                                        0.5f,
                                        Animation.RELATIVE_TO_SELF,
                                        0.5f
                                    )
                                    rotate!!.interpolator = LinearInterpolator()
                                    rotate!!.duration = 100
                                    barImageView.startAnimation(rotate)
                                    downloadTextView.text =
                                        dec.format(downloadTest.getInstantDownloadRate()) + " Mbps"
                                }
                                lastPosition = position

                                //Update chart
                                runOnUiThread { // Creating an  XYSeries for Income
                                    val downloadSeries = XYSeries("")
                                    downloadSeries.setTitle("")
                                    val tmpLs: List<Double> =
                                        ArrayList(downloadRateList)
                                    var count = 0.0
                                    for (`val` in tmpLs) {
                                        downloadSeries.add(count++, `val`)
                                    }
                                    val dataset = XYMultipleSeriesDataset()
                                    dataset.addSeries(downloadSeries)
                                    val chartView: GraphicalView =
                                        ChartFactory.getLineChartView(
                                            baseContext, dataset, multiDownloadRenderer
                                        )
                                    chartDownload.addView(chartView, 0)
                                }
                            }
                        }


                        //Upload Test
                        if (pingTestFinished&&downloadTestFinished) {
                            if (uploadTestFinished) {
                                //Failure
                                if (uploadTest.getFinalUploadRate() == 0.0) {
                                    println("Upload error...")
                                } else {
                                    //Success
                                    runOnUiThread {
                                        uploadTextView.text =
                                            dec.format(uploadTest.getFinalUploadRate()) + " Mbps"
                                    }
                                }
                            } else {
                                //Calc position
                                val uploadRate = uploadTest.instantUploadRate
                                //if (uploadRate>0)
                                uploadRateList.add(uploadRate)
                                position = getPositionByRate(uploadRate)
                                Log.d("Speed","${uploadRate} position ${position} ${lastPosition}")
                                runOnUiThread {
                                    rotate = RotateAnimation(
                                        lastPosition.toFloat(),
                                        position.toFloat(),
                                        Animation.RELATIVE_TO_SELF,
                                        0.5f,
                                        Animation.RELATIVE_TO_SELF,
                                        0.5f
                                    )
                                    rotate!!.interpolator = LinearInterpolator()
                                    rotate!!.duration = 100
                                    barImageView.startAnimation(rotate)
                                    uploadTextView.text =
                                        dec.format(uploadTest.instantUploadRate) + " Mbps"
                                }
                                lastPosition = position

                                //Update chart
                                runOnUiThread { // Creating an  XYSeries for Income
                                    val uploadSeries = XYSeries("")
                                    uploadSeries.setTitle("")
                                    var count = 0.0
                                    val tmpLs: List<Double> =
                                        ArrayList(uploadRateList)
                                    for (val1 in tmpLs) {
                                        var val2=val1
                                        if (count == 0.0) {
                                            val2 = 0.0
                                        }
                                        uploadSeries.add(count++, val2)
                                    }
                                    val dataset = XYMultipleSeriesDataset()
                                    dataset.addSeries(uploadSeries)
                                    val chartView: GraphicalView =
                                        ChartFactory.getLineChartView(
                                            baseContext, dataset, multiUploadRenderer
                                        )
                                    chartUpload.addView(chartView, 0)
                                }
                            }
                        }

                        //Test bitti

                        if (pingTest.isFinished) {
                            pingTestFinished = true
                        }
                        if (downloadTest.isFinished) {
                            downloadTestFinished = true
                        }
                        if (uploadTest.isFinished) {
                            uploadTestFinished = true
                        }
                        if (pingTestFinished && downloadTestFinished && uploadTestFinished) {
                            break
                        }
                        if (pingTestStarted && !pingTestFinished) {
                            try {
                                Thread.sleep(300)
                            } catch (e: InterruptedException) {
                            }
                        } else {
                            try {
                                Thread.sleep(100)
                            } catch (e: InterruptedException) {
                            }
                        }
                    }

                    //Thread bitiminde button yeniden aktif ediliyor
                    runOnUiThread {
                        startButton.isEnabled = true
                        startButton.textSize = 16f
                        startButton.text = "Restart Test"
                    }
                }
            }).start()

        }

    fun getPositionByRate(rate: Double): Int {
        if (rate <= 1) {
            return (rate * 30).toInt()
        } else if (rate <= 10) {
            return (rate * 6).toInt() + 30
        } else if (rate <= 30) {
            return ((rate - 10) * 3).toInt() + 90
        } else if (rate <= 50) {
            return ((rate - 30) * 1.5).toInt() + 150
        } else if (rate <= 100) {
            return ((rate - 50) * 1.2).toInt() + 180
        }
        return 0
    }

    companion object {
        var position = 0
        var lastPosition = 0
    }
}
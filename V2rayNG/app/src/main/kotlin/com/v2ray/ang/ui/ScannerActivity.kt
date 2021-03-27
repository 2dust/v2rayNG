package com.v2ray.ang.ui

import android.Manifest
import android.app.Activity
import android.os.Bundle
import com.google.zxing.Result
import me.dm7.barcodescanner.zxing.ZXingScannerView
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.Menu
import android.view.MenuItem
import com.google.zxing.BarcodeFormat
import com.tbruyelle.rxpermissions.RxPermissions
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.QRCodeDecoder

class ScannerActivity : BaseActivity(), ZXingScannerView.ResultHandler {
    companion object {
        private const val REQUEST_FILE_CHOOSER = 2
    }


    private var mScannerView: ZXingScannerView? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mScannerView = ZXingScannerView(this)   // Programmatically initialize the scanner view

        mScannerView?.setAutoFocus(true)
        val formats = ArrayList<BarcodeFormat>()
        formats.add(BarcodeFormat.QR_CODE)
        mScannerView?.setFormats(formats)

        setContentView(mScannerView)                // Set the scanner view as the content view

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    public override fun onResume() {
        super.onResume()
        mScannerView!!.setResultHandler(this) // Register ourselves as a handler for scan results.
        mScannerView!!.startCamera()          // Start camera on resume
    }

    public override fun onPause() {
        super.onPause()
        mScannerView!!.stopCamera()           // Stop camera on pause
    }

    override fun handleResult(rawResult: Result) {
        // Do something with the result here
//        Log.v(FragmentActivity.TAG, rawResult.text) // Prints scan results
//        Log.v(FragmentActivity.TAG, rawResult.barcodeFormat.toString()) // Prints the scan format (qrcode, pdf417 etc.)

        finished(rawResult.text)

        // If you would like to resume scanning, call this method below:
//        mScannerView!!.resumeCameraPreview(this)
    }

    fun finished(text: String) {
        val intent = Intent()
        intent.putExtra("SCAN_RESULT", text)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_scanner, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.select_photo -> {
            RxPermissions(this)
                    .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                    .subscribe {
                        if (it) {
                            try {
                                showFileChooser()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else
                            toast(R.string.toast_permission_denied)
                    }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        //intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.title_file_chooser)),
                    REQUEST_FILE_CHOOSER)
        } catch (ex: android.content.ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_FILE_CHOOSER -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    try {
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        val text = QRCodeDecoder.syncDecodeQRCode(bitmap)
                        finished(text)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        toast(e.message.toString())
                    }
                }
            }
        }
    }
}

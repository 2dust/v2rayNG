package com.v2ray.ang.ui

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.v2ray.ang.R
import com.v2ray.ang.util.Utils
import kotlinx.android.synthetic.main.activity_logcat.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread

import java.io.IOException
import java.util.LinkedHashSet

class LogcatActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)

        title = getString(R.string.title_logcat)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        logcat()
    }

    private fun logcat() {

        try {
            pb_waiting.visibility = View.VISIBLE

            doAsync {
                val lst = LinkedHashSet<String>()
                lst.add("logcat")
                lst.add("-d")
                lst.add("-v")
                lst.add("time")
                lst.add("-s")
                lst.add("GoLog,tun2socks,com.v2ray.ang")
                val process = Runtime.getRuntime().exec(lst.toTypedArray())
//                val bufferedReader = BufferedReader(
//                        InputStreamReader(process.inputStream))
//                val allText = bufferedReader.use(BufferedReader::readText)
                val allText = process.inputStream.bufferedReader().use { it.readText() }
                uiThread {
                    tv_logcat.text = allText
                    tv_logcat.movementMethod = ScrollingMovementMethod()
                    pb_waiting.visibility = View.GONE
                }
            }
        } catch (e: IOException) {
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_logcat, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.copy_all -> {
            Utils.setClipboard(this, tv_logcat.text.toString())
            toast(R.string.toast_success)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }
}

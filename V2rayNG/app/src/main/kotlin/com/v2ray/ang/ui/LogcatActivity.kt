package com.v2ray.ang.ui

import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLogcatBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

import java.io.IOException
import java.util.LinkedHashSet

class LogcatActivity : BaseActivity() {
    private lateinit var binding: ActivityLogcatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       binding = ActivityLogcatBinding.inflate(layoutInflater)
       val view = binding.root
       setContentView(view)

        title = getString(R.string.title_logcat)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        logcat(false)
    }

    private fun logcat(shouldFlushLog: Boolean) {

        try {
            binding.pbWaiting.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.Default) {
                if (shouldFlushLog) {
                    val lst = LinkedHashSet<String>()
                    lst.add("logcat")
                    lst.add("-c")
                    val process = Runtime.getRuntime().exec(lst.toTypedArray())
                    process.waitFor()
                }
                val lst = LinkedHashSet<String>()
                lst.add("logcat")
                lst.add("-d")
                lst.add("-v")
                lst.add("time")
                lst.add("-s")
                lst.add("GoLog,tun2socks,${ANG_PACKAGE},AndroidRuntime,System.err")
                val process = Runtime.getRuntime().exec(lst.toTypedArray())
//                val bufferedReader = BufferedReader(
//                        InputStreamReader(process.inputStream))
//                val allText = bufferedReader.use(BufferedReader::readText)
                val allText = process.inputStream.bufferedReader().use { it.readText() }
                var regex=Regex("""\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+([A-Z]\/[A-z.-]*)\s*\(?\d*\)?:?\|?\s*""")
                val regex_xray = Regex("""^\d{4}/\d{2}/\d{2}\s+\d{2}:\d{2}:\d{2}\s*""")

                var regex2=Regex("""  +""")

                launch(Dispatchers.Main) {
                    binding.tvLogcat.text = allText.replace(regex,"").replace(regex_xray,"").replace(regex2,"")
                    binding.tvLogcat.textDirection=View.TEXT_DIRECTION_LTR
                    binding.tvLogcat.movementMethod = ScrollingMovementMethod()
                    binding.tvLogcat.scrollTo(0,binding.tvLogcat.length())
                    binding.pbWaiting.visibility = View.GONE
                    Handler(Looper.getMainLooper()).post { binding.svLogcat.fullScroll(View.FOCUS_DOWN) }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logcat, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.copy_all -> {
            Utils.setClipboard(this, binding.tvLogcat.text.toString())
            toast(R.string.toast_success)
            true
        }
        R.id.clear_all -> {
            logcat(true)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}

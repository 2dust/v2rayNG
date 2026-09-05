package com.v2ray.ang.repository

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class LogcatRepository(private val app: Application) : BaseRepository() {

    /** Reads the most recent entries, newest first. Returns an empty list when logd refuses. */
    open suspend fun read(): List<String> = runIO(emptyList()) {
        val since = baseline()
        val command = buildList {
            add(CMD_LOGCAT)
            add("-d")
            add("-v")
            add("time")
            // Either everything logged after the last local "clear", or the last MAX_LINES rows.
            add("-t")
            add(since ?: MAX_LINES.toString())
            add("-s")
            add(TAGS)
        }
        exec(command)
    }

    /**
     * Clears the log. Best-effort on the system buffer, guaranteed on the client side.
     *
     * @return always true - from the user's point of view the screen is now empty and stays empty.
     */
    open suspend fun clear(): Boolean = runIO(true) {
        var process: Process? = null
        try {
            process = ProcessBuilder(listOf(CMD_LOGCAT, "-c")).redirectErrorStream(true).start()
            // Drain before waiting, otherwise a full pipe would block the child forever.
            process.inputStream.use { it.readBytes() }
            process.waitFor()
        } catch (e: Exception) {
            // Expected on most retail devices: logd rejects unprivileged clears. There is no
            // suspension point inside this block, so no CancellationException can be swallowed.
            LogUtil.w(AppConfig.TAG, "logcat -c refused, falling back to a local baseline", e)
        } finally {
            process?.destroy()
        }
        MmkvManager.encodeSettings(AppConfig.CACHE_LOGCAT_CLEARED_AT, stampForNextRead())
        true
    }

    /**
     * Writes the given lines into a shareable cache file, wiping previous exports.
     *
     * @return absolute path of the export, or null when it could not be written. A path keeps
     * `java.io.File` out of the ViewModel.
     */
    open suspend fun writeShareFile(lines: List<String>): String? = runIO(null) {
        val dir = File(app.cacheDir, SHARE_DIR_NAME).apply {
            deleteRecursively()
            mkdirs()
        }
        val stamp = SimpleDateFormat(FILE_STAMP_FORMAT, Locale.US).format(Date())
        val file = File(dir, "$SHARE_FILE_PREFIX$stamp$SHARE_FILE_SUFFIX")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            lines.forEach { line ->
                writer.append(line).append('\n')
            }
        }
        file.absolutePath
    }

    /** Clipboard writes are Binder calls, so they belong on the IO dispatcher. */
    open suspend fun copyToClipboard(text: String) = withIO { Utils.setClipboard(app, text) }

    // ---------------- internals ----------------

    /**
     * Runs the command and returns at most [MAX_LINES] lines, newest first.
     * A ring buffer keeps the footprint constant even if the command ignores `-t`.
     * Exceptions are intentionally not caught here: [runIO] logs them and applies the fallback.
     */
    private suspend fun exec(command: List<String>): List<String> {
        var process: Process? = null
        try {
            process = ProcessBuilder(command).redirectErrorStream(true).start()
            val ring = ArrayDeque<String>()
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    ring.addLast(line)
                    if (ring.size > MAX_LINES) ring.removeFirst()
                }
            }
            return ring.asReversed().toList()
        } finally {
            process?.destroy()
        }
    }

    /** The stored baseline, or null when it is absent or malformed. */
    private fun baseline(): String? = MmkvManager
        .decodeSettingsString(AppConfig.CACHE_LOGCAT_CLEARED_AT)
        ?.takeIf { STAMP_PATTERN.matches(it) }
        ?.takeIf { stamp ->
            try {
                val parsed = SimpleDateFormat(STAMP_FORMAT, Locale.US).parse(stamp)
                val now = Date()
                val diff = now.time - parsed.time
                diff in 0..365L * 24 * 60 * 60 * 1000
            } catch (_: Exception) { false }
        }

    /** "Now + 1 ms", so the boundary entry is not repeated after a clear. */
    private fun stampForNextRead(): String =
        SimpleDateFormat(STAMP_FORMAT, Locale.US).format(Date(System.currentTimeMillis() + 1))

    private companion object {
        const val CMD_LOGCAT = "logcat"

        /** Tags kept identical to the previous behaviour; System.err carries stack traces. */
        const val TAGS = "GoLog,${AppConfig.ANG_PACKAGE},AndroidRuntime,System.err"

        /** Upper bound of lines held in memory (~300 KB of strings at 150 chars per line). */
        const val MAX_LINES = 2000

        const val SHARE_DIR_NAME = "shared_logs"
        const val SHARE_FILE_PREFIX = "v2rayNG_logcat_"
        const val SHARE_FILE_SUFFIX = ".txt"
        const val FILE_STAMP_FORMAT = "yyyy-MM-dd_HH-mm-ss"

        /** Matches the `-v time` format that `logcat -t '<time>'` expects. */
        const val STAMP_FORMAT = "MM-dd HH:mm:ss.SSS"
        val STAMP_PATTERN = Regex("""\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}""")
    }
}

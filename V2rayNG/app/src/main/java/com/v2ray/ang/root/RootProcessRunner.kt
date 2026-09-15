package com.v2ray.ang.root

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Owns one bounded root command. Neither inherited stdout nor output volume extends its deadline. */
internal object RootProcessRunner {
    data class Outcome(val code: Int, val output: String)

    fun run(command: List<String>, timeoutMillis: Long, outputLimit: Int = 65536): Outcome {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val captured = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var code = -1
        try {
            // Process.waitFor(timeout, unit) is API 26. Polling supports API 24–25 too.
            // Read only available bytes: a detached helper may retain stdout after su exits.
            while (System.nanoTime() < deadline) {
                val available = process.inputStream.available()
                if (available > 0) {
                    val size = process.inputStream.read(buffer, 0, minOf(buffer.size, available))
                    val remaining = outputLimit - captured.size()
                    if (size > 0 && remaining > 0) captured.write(buffer, 0, minOf(size, remaining))
                }
                try {
                    code = process.exitValue()
                    if (process.inputStream.available() == 0) break
                } catch (_: IllegalThreadStateException) {
                    if (available == 0) Thread.sleep(10)
                }
            }
        } finally {
            if (code == -1) process.destroy()
            process.inputStream.close()
            process.outputStream.close()
            process.errorStream.close()
        }
        return Outcome(code, captured.toString("UTF-8"))
    }
}

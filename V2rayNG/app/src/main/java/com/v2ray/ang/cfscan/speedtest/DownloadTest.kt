package com.v2ray.ang.cfscan.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.math.round

/**
 * 下载速度测试类
 * 参考 Windows 版 cfst.exe 的测速逻辑优化
 * 
 * Windows 原版逻辑：
 * - 下载测试时间：10 秒
 * - 使用单连接下载（不是多线程并发）
 * - 测速 URL：https://cf.xiu2.xyz/url (可自建)
 * - 计算方式：总字节数 / 实际耗时
 */
class DownloadTest {

    companion object {
        // 下载测速 URL - 使用官方测速地址，同时改用 http 以避免由于国内 HTTPS SNI 阻断导致的连接重置，解决 cf.xiu2.xyz 403 Forbidden 的问题。
        // bytes=50000000 代表单次请求 50MB。
        private const val DOWNLOAD_URL = "http://speed.cloudflare.com/__down?bytes=50000000"
        private const val HOST_NAME = "speed.cloudflare.com"

        /**
         * 创建 OkHttpClient 用于测速
         * 使用自定义 DNS 解析强制请求发送到指定 IP
         */
        private fun createClientForIp(ip: String): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        // 强制将测速域名的 DNS 解析结果指向我们要测速的 Cloudflare IP
                        if (hostname == HOST_NAME) {
                            return try {
                                listOf(InetAddress.getByName(ip))
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                        return Dns.SYSTEM.lookup(hostname)
                    }
                })
                .build()
        }

        /**
         * 执行下载速度测试（挂起函数）
         *
         * 参考 Windows 原版 cfst.exe 的逻辑：
         * - 单连接下载（不是多线程并发）
         * - 下载时长默认 10 秒
         * - 持续读取数据直到超时或完成，如果不到10秒就下载完了，则继续发起请求
         *
         * @param ip 目标 IP 地址
         * @param testDurationMs 测试时长 (毫秒)，默认 10000ms (10 秒)
         * @return 下载速度 (MB/s)，失败返回 0.0
         */
        suspend fun testDownloadSpeed(
            ip: String,
            testDurationMs: Long = 10000  // 改为 10 秒，与 Windows 原版一致
        ): Double = withContext(Dispatchers.IO) {
            val client = createClientForIp(ip)

            var downloadedBytes = 0L
            var actualElapsedMs = 0L

            val downloadStartTime = System.currentTimeMillis()
            val deadline = downloadStartTime + testDurationMs

            try {
                // 通过 while 循环保持测速，直到 10 秒倒计时结束。应对 Gigabit 带宽 50MB 瞬间跑完的情况。
                while (System.currentTimeMillis() < deadline) {
                    val request = Request.Builder()
                        .url(DOWNLOAD_URL)
                        .header("Host", HOST_NAME)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "*/*")
                        .header("Connection", "close")
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { inputStream ->
                            val buffer = ByteArray(131072) // 128KB 缓冲区
                            var bytesRead: Int

                            while (System.currentTimeMillis() < deadline) {
                                bytesRead = inputStream.read(buffer)
                                if (bytesRead == -1) break
                                downloadedBytes += bytesRead
                            }
                        }
                    } else {
                        // 如果响应失败 (如 403 Forbidden)，直接跳出防止无效死循环空跑
                        break
                    }
                }

                actualElapsedMs = System.currentTimeMillis() - downloadStartTime
            } catch (e: Exception) {
                // 如果是发生超时/中断等异常，我们仍可以拿已下载的字节数计算速度
                actualElapsedMs = System.currentTimeMillis() - downloadStartTime
            }

            // 计算下载速度
            if (actualElapsedMs > 0 && downloadedBytes > 0) {
                val speedBps = (downloadedBytes * 1000.0) / actualElapsedMs
                val speedMBps = speedBps / (1024 * 1024)
                round(speedMBps * 100) / 100
            } else {
                0.0
            }
        }
    }
}

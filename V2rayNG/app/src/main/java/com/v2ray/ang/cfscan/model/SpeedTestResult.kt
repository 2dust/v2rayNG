package com.v2ray.ang.cfscan.model

import java.util.Date

/**
 * 测速结果数据类
 */
data class SpeedTestResult(
    val ipAddress: String,          // IP地址
    val packetsSent: Int,           // 发送的包数量
    val packetsReceived: Int,       // 接收的包数量
    val packetLoss: Double,         // 丢包率 (0.00 - 100.00)
    val avgLatency: Double,         // 平均延迟 (ms)
    val downloadSpeed: Double,      // 下载速度 (MB/s)
    val regionCode: String,         // 地区码
    val testTime: Long = System.currentTimeMillis()  // 测试时间戳
) : Comparable<SpeedTestResult> {
    
    override fun compareTo(other: SpeedTestResult): Int {
        // 先按丢包率升序，再按延迟升序，最后按下载速度降序
        val lossCompare = this.packetLoss.compareTo(other.packetLoss)
        if (lossCompare != 0) return lossCompare
        
        val latencyCompare = this.avgLatency.compareTo(other.avgLatency)
        if (latencyCompare != 0) return latencyCompare
        
        return other.downloadSpeed.compareTo(this.downloadSpeed)
    }

    fun getTestTimeFormatted(): String {
        val date = Date(this.testTime)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(date)
    }
    
    fun toCsvString(): String {
        return "$ipAddress,$packetsSent,$packetsReceived,${String.format("%.2f", packetLoss)},${String.format("%.2f", avgLatency)},${String.format("%.2f", downloadSpeed)},$regionCode,${getTestTimeFormatted()}"
    }
    
    companion object {
        fun getCsvHeader(): String {
            return "IP 地址,已发送,已接收,丢包率,平均延迟,下载速度(MB/s),地区码,测试时间"
        }
    }
}

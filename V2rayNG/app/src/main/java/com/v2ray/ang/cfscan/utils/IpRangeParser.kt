package com.v2ray.ang.cfscan.utils

import java.util.Random
import java.math.BigInteger

/**
 * IP 段解析和随机 IP 生成工具类
 * 支持 CIDR 格式的 IP 段，如 173.245.48.0/20 (IPv4) 和 2400:cb00:2049::/48 (IPv6)
 */
class IpRangeParser {

    /**
     * IP 范围数据类
     * 使用 BigInteger 存储 IP 地址以同时支持 IPv4 和 IPv6
     */
    data class IpRange(
        val startIp: BigInteger,
        val endIp: BigInteger,
        val isIpv6: Boolean = false
    )

    companion object {
        private val random = Random()

        /**
         * 解析 CIDR 格式的 IP 段（支持 IPv4 和 IPv6）
         * @param cidr CIDR 格式字符串，如 "173.245.48.0/20" 或 "2400:cb00:2049::/48"
         * @return IpRange 对象，包含起始和结束 IP
         */
        fun parseCidr(cidr: String): IpRange? {
            return try {
                val parts = cidr.trim().split("/")
                if (parts.size != 2) return null

                val ip = parts[0].trim()
                val prefixLength = parts[1].toIntOrNull() ?: return null

                // 判断是 IPv4 还是 IPv6
                val isIpv6 = ip.contains(':')

                if (isIpv6) {
                    // IPv6 处理
                    if (prefixLength < 0 || prefixLength > 128) return null

                    val fullIp = expandIpv6(ip) ?: return null
                    val ipBigInt = ipv6ToBigInteger(fullIp)

                    // 计算网络掩码：前 prefixLength 位为 1，其余为 0
                    // 例如 /48 表示前 48 位是网络位
                    val mask = if (prefixLength == 0) {
                        BigInteger.ZERO
                    } else if (prefixLength == 128) {
                        BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
                    } else {
                        // 创建前缀掩码：高 prefixLength 位为 1
                        BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
                            .xor(BigInteger.ONE.shiftLeft(128 - prefixLength).subtract(BigInteger.ONE))
                    }

                    val networkAddress = ipBigInt.and(mask)
                    // 广播地址：网络地址 + 主机部分全 1
                    val hostBits = 128 - prefixLength
                    val broadcastAddress = if (hostBits == 0) {
                        networkAddress
                    } else {
                        networkAddress.add(BigInteger.ONE.shiftLeft(hostBits).subtract(BigInteger.ONE))
                    }

                    // 对于 /128 这样的单个 IP，直接返回
                    if (prefixLength == 128) {
                        IpRange(networkAddress, networkAddress, isIpv6 = true)
                    } else {
                        // 排除网络地址和广播地址
                        IpRange(
                            networkAddress.add(BigInteger.ONE),
                            broadcastAddress.subtract(BigInteger.ONE),
                            isIpv6 = true
                        )
                    }
                } else {
                    // IPv4 处理
                    if (prefixLength < 0 || prefixLength > 32) return null

                    val ipLong = ipToLong(ip) ?: return null
                    val mask = if (prefixLength == 0) 0L else (-1L shl (32 - prefixLength))
                    val networkAddress = ipLong and mask
                    val broadcastAddress = networkAddress or mask.inv()

                    IpRange(
                        BigInteger.valueOf(networkAddress + 1),
                        BigInteger.valueOf(broadcastAddress - 1),
                        isIpv6 = false
                    )
                }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 展开 IPv6 地址的压缩格式
         * 例如：2400:cb00:2049::/48 -> 2400:cb00:2049:0000:0000:0000:0000:0000
         */
        private fun expandIpv6(ipv6: String): String? {
            return try {
                // 移除可能存在的方括号和前后空格
                var cleanIp = ipv6.trim('[', ']', ' ')
                
                // 如果包含 CIDR 前缀，移除它
                if ('/' in cleanIp) {
                    cleanIp = cleanIp.substringBefore('/')
                }
                
                // 处理 :: 压缩
                if ("::" in cleanIp) {
                    val parts = cleanIp.split("::")
                    val leftParts = if (parts[0].isEmpty()) emptyList() else parts[0].split(':').filter { it.isNotEmpty() }
                    val rightParts = if (parts.size > 1 && parts[1].isEmpty()) emptyList() 
                                     else if (parts.size > 1) parts[1].split(':').filter { it.isNotEmpty() } 
                                     else emptyList()

                    val missingParts = 8 - leftParts.size - rightParts.size
                    if (missingParts < 0) return null
                    
                    val middleParts = List(missingParts) { "0000" }

                    val allParts = (leftParts + middleParts + rightParts)
                        .map { it.padStart(4, '0') }
                    
                    if (allParts.size != 8) return null

                    allParts.joinToString(":")
                } else {
                    // 没有压缩，直接展开
                    val parts = cleanIp.split(':').filter { it.isNotEmpty() }
                    if (parts.size != 8) return null
                    parts
                        .map { it.padStart(4, '0') }
                        .joinToString(":")
                }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 将展开的 IPv6 字符串转换为 BigInteger
         */
        private fun ipv6ToBigInteger(ipv6: String): BigInteger {
            val parts = ipv6.split(':')
            var result = BigInteger.ZERO

            for (part in parts) {
                val value = part.toIntOrNull(16) ?: 0
                result = result.shiftLeft(16).or(BigInteger.valueOf(value.toLong()))
            }

            return result
        }

        /**
         * 从 IP 范围中随机选择一个 IP（支持 IPv4 和 IPv6）
         */
        fun getRandomIpFromRange(range: IpRange, random: Random = this.random): String {
            val rangeSize = range.endIp.subtract(range.startIp).add(BigInteger.ONE)
            if (rangeSize.compareTo(BigInteger.ONE) <= 0) {
                return if (range.isIpv6) {
                    bigIntegerToIpv6(range.startIp)
                } else {
                    longToIp(range.startIp.toLong())
                }
            }

            val randomOffset = if (range.isIpv6 && rangeSize.compareTo(BigInteger.valueOf(0xFFFF)) > 0) {
                // [关键修复]：对于 IPv6 (如 /32, /48)，它的地址池庞大到离谱（例如 /48 是 2^80 个 IP）。
                // Cloudflare 等 CDN 绝对不可能在池里的每一个随机叶子节点上绑定服务器监听。
                // 如果我们随意随机分布在巨大的 Host 区间内，高达 99.999% 的概率会命中一个无人监听的黑洞 IP。
                // 结果就是去 Ping 黑洞毫无响应，APP 会误判认为是 50 次连续失败导致网络断网。
                // 解决方案：采用 Anycast CDN 通用测速逻辑，锁定前置网络段位，并严格只随机最后 16 位主机号 (0x0000 ~ 0xFFFF)
                BigInteger(16, random).mod(BigInteger.valueOf(0x10000))
            } else if (rangeSize.bitLength() < 64) {
                BigInteger.valueOf((random.nextDouble() * rangeSize.toDouble()).toLong())
            } else {
                // 对于非常大的范围，使用随机 BigInteger
                BigInteger(rangeSize.bitLength(), random).mod(rangeSize)
            }

            val ipBigInt = range.startIp.add(randomOffset)

            return if (range.isIpv6) {
                bigIntegerToIpv6(ipBigInt)
            } else {
                longToIp(ipBigInt.toLong())
            }
        }

        /**
         * 从多个 IP 范围中随机选择一个 IP（支持 IPv4 和 IPv6）
         */
        fun getRandomIpFromRanges(ranges: List<IpRange>, random: Random = this.random): String? {
            if (ranges.isEmpty()) return null

            // 首先根据每个范围的 IP 数量加权随机选择一个范围
            val totalIps = ranges.sumOf { it.endIp.subtract(it.startIp).add(BigInteger.ONE) }
            var randomValue = if (totalIps.bitLength() < 64) {
                BigInteger.valueOf((random.nextDouble() * totalIps.toDouble()).toLong())
            } else {
                BigInteger(totalIps.bitLength(), random).mod(totalIps)
            }

            for (range in ranges) {
                val rangeSize = range.endIp.subtract(range.startIp).add(BigInteger.ONE)
                if (randomValue < rangeSize) {
                    return getRandomIpFromRange(range, random)
                }
                randomValue = randomValue.subtract(rangeSize)
            }

            return getRandomIpFromRange(ranges.last(), random)
        }

        /**
         * 解析 IP 文件，返回 IP 范围列表
         */
        fun parseIpFile(content: String): List<IpRange> {
            return content.replace("\uFEFF", "")
                .lines()
                .mapNotNull { line ->
                    val trimmedLine = line.trim().removePrefix("\uFEFF")
                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) null
                    else parseCidr(trimmedLine)
                }
                .filter { it.endIp >= it.startIp }
        }

        /**
         * IP 字符串转 Long（仅 IPv4）
         */
        fun ipToLong(ip: String): Long? {
            return try {
                val parts = ip.split(".")
                if (parts.size != 4) return null

                var result = 0L
                for (part in parts) {
                    val num = part.toIntOrNull() ?: return null
                    if (num < 0 || num > 255) return null
                    result = (result shl 8) or num.toLong()
                }
                result
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Long 转 IP 字符串（仅 IPv4）
         */
        fun longToIp(ip: Long): String {
            return "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"
        }

        /**
         * BigInteger 转 IPv6 字符串
         */
        private fun bigIntegerToIpv6(value: BigInteger): String {
            var bigInteger = value.and(BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE))
            val parts = mutableListOf<String>()

            repeat(8) {
                parts.add(bigInteger.and(BigInteger.valueOf(0xFFFF.toLong())).toString(16).padStart(4, '0'))
                bigInteger = bigInteger.shiftRight(16)
            }

            // 压缩连续的 0000 段为 ::
            return compressIpv6(parts.reversed().joinToString(":"))
        }

        /**
         * 压缩 IPv6 地址中的连续 0 段
         */
        private fun compressIpv6(ipv6: String): String {
            val parts = ipv6.split(':')

            // 找到最长的连续 0 段
            var maxStart = -1
            var maxLen = 0
            var currentStart = -1
            var currentLen = 0

            for (i in parts.indices) {
                if (parts[i] == "0000" || parts[i] == "0") {
                    if (currentStart == -1) {
                        currentStart = i
                        currentLen = 1
                    } else {
                        currentLen++
                    }
                } else {
                    if (currentLen > maxLen) {
                        maxStart = currentStart
                        maxLen = currentLen
                    }
                    currentStart = -1
                    currentLen = 0
                }
            }

            if (currentLen > maxLen) {
                maxStart = currentStart
                maxLen = currentLen
            }

            // 只有当连续 0 段长度 >= 2 时才压缩
            if (maxLen < 2) {
                return parts.joinToString(":")
            }

            val leftParts = parts.subList(0, maxStart).map { it.replaceFirst("^0+".toRegex(), "").ifEmpty { "0" } }
            val rightParts = parts.subList(maxStart + maxLen, parts.size).map { it.replaceFirst("^0+".toRegex(), "").ifEmpty { "0" } }

            return when {
                leftParts.isEmpty() && rightParts.isEmpty() -> "::"
                leftParts.isEmpty() -> "::" + rightParts.joinToString(":")
                rightParts.isEmpty() -> leftParts.joinToString(":") + "::"
                else -> leftParts.joinToString(":") + "::" + rightParts.joinToString(":")
            }
        }

        /**
         * 获取 IP 范围的总 IP 数量
         */
        fun getTotalIpCount(ranges: List<IpRange>): BigInteger {
            return ranges.sumOf { it.endIp.subtract(it.startIp).add(BigInteger.ONE) }
        }

        /**
         * 判断 IP 地址是否为 IPv6
         */
        fun isIpv6Address(ip: String): Boolean {
            return ip.contains(':')
        }
    }
}

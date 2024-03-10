package com.v2ray.ang.util

object IpUtil {
    fun getRandomIpList (cidrList: Array<String>, maxPerRange: Int = 3): List<String> {
        var ipList: MutableList<String> = mutableListOf()

        cidrList.forEach { cidr ->
            getChoppedCidr(cidr).forEach { ip ->
                val ipParts = ip.split(".").toTypedArray()
                (1..254).shuffled().take(maxPerRange).forEach {
                    ipParts[3] = it.toString()
                    ipList += ipParts.joinToString(".")
                }
            }
        }

        return ipList.shuffled()
    }

    private fun getChoppedCidr(cidr: String): Array<String> {
        val ip = cidr.split('/')[0]
        return arrayOf(ip)
    }
}


package com.ankiride.bridge.mywhoosh

import java.net.InetAddress

object LanAddressValidator {
    private val IPV4_LITERAL_REGEX = Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    fun isAllowedTargetHost(host: String): Boolean {
        if (!IPV4_LITERAL_REGEX.matches(host)) return false
        return try {
            val addr = InetAddress.getByName(host)
            addr.isLoopbackAddress || addr.isSiteLocalAddress
        } catch (_: Exception) {
            false
        }
    }
}

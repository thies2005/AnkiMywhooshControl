package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

open class DesktopMdnsAdvertiser {
    private var jmdns: JmDNS? = null

    open fun registerOpenBikeService(port: Int = 21587) {
        try {
            val lanAddress = findPrimaryLanIpv4() ?: InetAddress.getLocalHost()
            val mdns = JmDNS.create(lanAddress, "AnkiRideDesktop")
            val props = mapOf(
                "version" to "1",
                "id" to "ankiridedesktop01",
                "name" to "AnkiRide Bridge",
                "service-uuids" to "d273f680-d548-419d-b9d1-fa0472345229",
                "manufacturer" to "AnkiRide",
                "model" to "Zwift Click v2 Desktop Bridge"
            )
            val serviceInfo = ServiceInfo.create(
                "_openbikecontrol._tcp.local.",
                "AnkiRideBridge",
                port,
                0,
                0,
                props
            )
            mdns.registerService(serviceInfo)
            jmdns = mdns
            DiagnosticLogger.info(
                LogCategory.MYWHOOSH,
                "JmDNS registered _openbikecontrol._tcp.local. on ${lanAddress.hostAddress}:$port"
            )
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.MYWHOOSH, "JmDNS registration failed: ${e.message}")
        }
    }

    open fun unregister() {
        try {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        } catch (_: Exception) {
        }
        jmdns = null
    }

    open fun close() = unregister()

    private fun findPrimaryLanIpv4(): InetAddress? {
        return NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
    }
}

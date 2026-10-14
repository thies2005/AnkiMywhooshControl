package com.ankiride.bridge.mywhoosh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

class MyWhooshDiscovery(private val context: Context) {
    companion object {
        private const val TAG = "MyWhooshDiscovery"
        const val SERVICE_TYPE_OPENBIKECONTROL = "_openbikecontrol._tcp."
        const val SERVICE_TYPE_BIKECONTROL = "_bikecontrol._tcp."
    }

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private val _discoveredIps = MutableStateFlow<Set<String>>(emptySet())
    val discoveredIps: StateFlow<Set<String>> = _discoveredIps.asStateFlow()

    private var isDiscovering = false
    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started for: $regType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
            resolveService(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Start discovery failed: $errorCode")
            isDiscovering = false
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Stop discovery failed: $errorCode")
            isDiscovering = false
        }
    }

    fun startDiscovery() {
        val manager = nsdManager ?: return
        if (isDiscovering) return
        try {
            isDiscovering = true
            manager.discoverServices(
                SERVICE_TYPE_OPENBIKECONTROL,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start mDNS discovery", e)
            isDiscovering = false
        }
    }

    fun stopDiscovery() {
        val manager = nsdManager ?: return
        if (!isDiscovering) return
        try {
            manager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop mDNS discovery", e)
        } finally {
            isDiscovering = false
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val manager = nsdManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.registerServiceInfoCallback(
                serviceInfo,
                Executors.newSingleThreadExecutor(),
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                    override fun onServiceUpdated(info: NsdServiceInfo) {
                        handleResolvedInfo(info)
                    }
                    override fun onServiceLost() {}
                    override fun onServiceInfoCallbackUnregistered() {}
                }
            )
        } else {
            @Suppress("DEPRECATION")
            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(info: NsdServiceInfo) {
                    handleResolvedInfo(info)
                }
            })
        }
    }

    private fun handleResolvedInfo(info: NsdServiceInfo) {
        val host = info.host?.hostAddress ?: return
        if (LanAddressValidator.isAllowedTargetHost(host)) {
            _discoveredIps.value = _discoveredIps.value + host
        }
    }
}

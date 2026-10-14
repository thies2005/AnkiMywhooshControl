package com.ankiride.bridge.mywhoosh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.ankiride.bridge.core.MyWhooshAction
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class OpenBikeControlClient(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        const val SERVICE_TYPE = "_openbikecontrol._tcp."
        const val SERVICE_NAME = "AnkiRideBridge"
        const val MSG_BUTTON_STATE: Byte = 0x01
        const val MSG_DEVICE_STATUS: Byte = 0x02
        const val STATE_PRESSED: Byte = 0x01
        const val STATE_RELEASED: Byte = 0x00
    }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    val connectedClients = CopyOnWriteArrayList<Socket>()

    fun startServer(port: Int = 21587) {
        if (serverSocket != null) return
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val srv = ServerSocket(port)
                serverSocket = srv
                registerMdnsService(srv.localPort)
                while (isActive && !srv.isClosed) {
                    val client = srv.accept()
                    val remoteHost = client.inetAddress?.hostAddress ?: ""
                    if (LanAddressValidator.isLocalOrLanAddress(remoteHost)) {
                        connectedClients.add(client)
                        // Send initial OpenBikeControl 0x02 Device Status (100% battery, connected flag = 1)
                        sendRawBytes(client, byteArrayOf(MSG_DEVICE_STATUS, 0x64, 0x01))
                    } else {
                        client.close()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun hasConnectedClients(): Boolean = connectedClients.any { !it.isClosed && it.isConnected }

    suspend fun sendAction(ip: String, port: Int, action: MyWhooshAction): Boolean = withContext(Dispatchers.IO) {
        if (!LanAddressValidator.isLocalOrLanAddress(ip)) return@withContext false

        val pressPacket = byteArrayOf(MSG_BUTTON_STATE, action.openBikeOpcode, STATE_PRESSED)
        val releasePacket = byteArrayOf(MSG_BUTTON_STATE, action.openBikeOpcode, STATE_RELEASED)

        var delivered = false
        for (client in connectedClients) {
            if (client.isClosed || !client.isConnected) {
                connectedClients.remove(client)
                continue
            }
            if (sendRawBytes(client, pressPacket)) {
                delivered = true
            } else {
                connectedClients.remove(client)
            }
        }
        if (delivered) {
            delay(35L)
            for (client in connectedClients) {
                sendRawBytes(client, releasePacket)
            }
        }
        delivered
    }

    private fun sendRawBytes(socket: Socket, bytes: ByteArray): Boolean {
        return try {
            val out: OutputStream = socket.getOutputStream()
            synchronized(out) {
                out.write(bytes)
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun registerMdnsService(port: Int) {
        val ctx = context ?: return
        val manager = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("v", "1")
            setAttribute("version", "1")
            setAttribute("name", "AnkiRide Bridge")
            setAttribute("model", "Zwift Click v2 Bridge")
            setAttribute("proto", "tcp")
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {}
            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    fun stopServer() {
        acceptJob?.cancel()
        registrationListener?.let { listener ->
            try {
                nsdManager?.unregisterService(listener)
            } catch (_: Exception) {
            }
        }
        registrationListener = null
        connectedClients.forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        connectedClients.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    fun close() {
        stopServer()
    }
}

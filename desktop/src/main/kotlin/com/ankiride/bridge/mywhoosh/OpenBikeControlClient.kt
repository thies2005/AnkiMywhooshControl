package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.MyWhooshAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class OpenBikeControlClient(
    private val mdnsAdvertiser: DesktopMdnsAdvertiser = DesktopMdnsAdvertiser(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        const val SERVICE_TYPE = "_openbikecontrol._tcp."
        const val SERVICE_NAME = "AnkiRideBridge"
        const val OPENBIKE_BLE_SERVICE_UUID = "d273f680-d548-419d-b9d1-fa0472345229"
        const val MSG_BUTTON_STATE: Byte = 0x01
        const val MSG_DEVICE_STATUS: Byte = 0x02
        const val MSG_HAPTIC_FEEDBACK: Byte = 0x03
        const val MSG_APP_INFO: Byte = 0x04
        const val STATE_PRESSED: Byte = 0x01
        const val STATE_RELEASED: Byte = 0x00
        const val PRESS_HOLD_MS = 90L
    }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    val connectedClients = CopyOnWriteArrayList<Socket>()

    private val _connectedAppInfo = MutableStateFlow<String?>(null)
    val connectedAppInfo: StateFlow<String?> = _connectedAppInfo.asStateFlow()

    fun connectedClientCount(): Int = connectedClients.count { !it.isClosed && it.isConnected }

    fun hasConnectedClients(): Boolean = connectedClientCount() > 0

    fun startServer(port: Int = 21587) {
        if (serverSocket != null) return
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val srv = ServerSocket(port)
                serverSocket = srv
                DiagnosticLogger.info(
                    LogCategory.MYWHOOSH,
                    "OpenBikeControl TCP Server listening on port ${srv.localPort}"
                )
                mdnsAdvertiser.registerOpenBikeService(srv.localPort)

                while (isActive && !srv.isClosed) {
                    val client = srv.accept()
                    val rawHost = client.inetAddress?.hostAddress ?: ""
                    if (LanAddressValidator.isLocalOrLanAddress(rawHost)) {
                        client.tcpNoDelay = true
                        connectedClients.add(client)
                        DiagnosticLogger.info(
                            LogCategory.MYWHOOSH,
                            "MyWhoosh connected from $rawHost (tcpNoDelay=true, clients=${connectedClients.size})"
                        )
                        // Send initial OpenBikeControl 0x02 Device Status (100% battery, connected = 1)
                        sendRawBytes(client, byteArrayOf(MSG_DEVICE_STATUS, 0x64, 0x01))
                        listenToClientMessages(client, rawHost)
                    } else {
                        DiagnosticLogger.warn(LogCategory.MYWHOOSH, "Rejected non-LAN socket from $rawHost")
                        client.close()
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    DiagnosticLogger.error(LogCategory.MYWHOOSH, "OpenBikeControl server error: ${e.message}")
                }
            }
        }
    }

    private fun listenToClientMessages(client: Socket, host: String) {
        scope.launch(Dispatchers.IO) {
            val buf = ByteArray(256)
            try {
                val input: InputStream = client.getInputStream()
                while (isActive && !client.isClosed) {
                    val readCount = input.read(buf)
                    if (readCount <= 0) break
                    val packet = buf.copyOfRange(0, readCount)
                    parseIncomingAppPacket(host, packet)
                }
            } catch (_: Exception) {
            } finally {
                connectedClients.remove(client)
                if (connectedClients.isEmpty()) {
                    _connectedAppInfo.value = null
                }
                DiagnosticLogger.warn(LogCategory.MYWHOOSH, "MyWhoosh client $host disconnected")
            }
        }
    }

    private fun parseIncomingAppPacket(host: String, data: ByteArray) {
        if (data.isEmpty()) return
        when (data[0]) {
            MSG_APP_INFO -> {
                try {
                    if (data.size >= 5) {
                        val ver = data[1].toInt() and 0xFF
                        val idLen = data[2].toInt() and 0xFF
                        val appId = if (3 + idLen <= data.size) String(data, 3, idLen, Charsets.UTF_8) else "?"
                        val verIdx = 3 + idLen
                        val verLen = if (verIdx < data.size) data[verIdx].toInt() and 0xFF else 0
                        val appVer = if (verIdx + 1 + verLen <= data.size) String(data, verIdx + 1, verLen, Charsets.UTF_8) else "?"
                        val btnCountIdx = verIdx + 1 + verLen
                        val btnCount = if (btnCountIdx < data.size) data[btnCountIdx].toInt() and 0xFF else 0
                        val btnsHex = if (btnCountIdx + 1 < data.size) {
                            data.copyOfRange(btnCountIdx + 1, data.size).joinToString(" ") { "0x%02X".format(it) }
                        } else "ALL"

                        val parsedInfo = "$appId v$appVer"
                        _connectedAppInfo.value = parsedInfo

                        DiagnosticLogger.info(
                            LogCategory.MYWHOOSH,
                            "Received 0x04 AppInfo from $host: app='$appId' v$appVer (proto=$ver, supportedButtons($btnCount)=[$btnsHex])"
                        )
                    }
                } catch (_: Exception) {
                    val hex = data.joinToString(" ") { "%02X".format(it) }
                    DiagnosticLogger.info(LogCategory.MYWHOOSH, "Received 0x04 AppInfo from $host: [$hex]")
                }
            }
            MSG_HAPTIC_FEEDBACK -> {
                val hex = data.joinToString(" ") { "%02X".format(it) }
                DiagnosticLogger.info(LogCategory.MYWHOOSH, "MyWhoosh confirmed action with 0x03 Haptic packet: [$hex]")
            }
            else -> {
                val hex = data.joinToString(" ") { "%02X".format(it) }
                DiagnosticLogger.info(LogCategory.MYWHOOSH, "Incoming packet from MyWhoosh ($host): [$hex]")
            }
        }
    }

    suspend fun sendAction(action: MyWhooshAction): Boolean {
        return sendAction("127.0.0.1", 21587, action)
    }

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
            DiagnosticLogger.info(
                LogCategory.MYWHOOSH,
                "Sent OpenBikeControl 0x01 PRESS [01 ${"%02X".format(action.openBikeOpcode)} 01] (${action.name}) -> holding ${PRESS_HOLD_MS}ms"
            )
            delay(PRESS_HOLD_MS)
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

    fun stopServer() {
        acceptJob?.cancel()
        acceptJob = null

        mdnsAdvertiser.unregister()

        connectedClients.forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        connectedClients.clear()
        _connectedAppInfo.value = null

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

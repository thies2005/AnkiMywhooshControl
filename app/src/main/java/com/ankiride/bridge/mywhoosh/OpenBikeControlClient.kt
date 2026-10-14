package com.ankiride.bridge.mywhoosh

import android.util.Log
import com.ankiride.bridge.core.MyWhooshAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class OpenBikeControlClient(
    private val socketFactory: () -> Socket = { Socket() }
) {
    companion object {
        private const val TAG = "OpenBikeControlClient"
        const val DEFAULT_PORT = 21587
        const val CONNECT_TIMEOUT_MS = 1500
        const val SOCKET_TIMEOUT_MS = 2000

        const val MSG_BUTTON_STATE: Byte = 0x01
        const val STATE_RELEASED: Byte = 0x00
        const val STATE_PRESSED: Byte = 0x01

        // Standard OpenBikeControl button IDs
        const val BTN_SHIFT_UP: Byte = 0x01
        const val BTN_SHIFT_DOWN: Byte = 0x02
        const val BTN_STEER_LEFT: Byte = 0x18
        const val BTN_STEER_RIGHT: Byte = 0x19
        const val BTN_SELECT: Byte = 0x14
        const val BTN_BACK: Byte = 0x21
        const val BTN_TOGGLE_UI: Byte = 0x30
        const val BTN_EMOTE: Byte = 0x31
        const val BTN_CAMERA_VIEW: Byte = 0x32
        const val BTN_POWER_UP: Byte = 0x33
        const val BTN_SCREENSHOT: Byte = 0x34

        fun mapActionToButtonId(action: MyWhooshAction): Byte {
            return when (action) {
                MyWhooshAction.SHIFT_UP -> BTN_SHIFT_UP
                MyWhooshAction.SHIFT_DOWN -> BTN_SHIFT_DOWN
                MyWhooshAction.STEER_LEFT -> BTN_STEER_LEFT
                MyWhooshAction.STEER_RIGHT -> BTN_STEER_RIGHT
                MyWhooshAction.SELECT -> BTN_SELECT
                MyWhooshAction.BACK -> BTN_BACK
                MyWhooshAction.TOGGLE_UI -> BTN_TOGGLE_UI
                MyWhooshAction.EMOTE -> BTN_EMOTE
                MyWhooshAction.CAMERA_VIEW -> BTN_CAMERA_VIEW
                MyWhooshAction.POWER_UP -> BTN_POWER_UP
                MyWhooshAction.SCREENSHOT -> BTN_SCREENSHOT
            }
        }

        fun createButtonPacket(buttonId: Byte, isPressed: Boolean): ByteArray {
            val state = if (isPressed) STATE_PRESSED else STATE_RELEASED
            return byteArrayOf(MSG_BUTTON_STATE, buttonId, state)
        }
    }

    private val mutex = Mutex()
    private var activeSocket: Socket? = null
    private var activeHost: String? = null
    private var activePort: Int = -1

    suspend fun sendAction(host: String, port: Int, action: MyWhooshAction): Boolean = withContext(Dispatchers.IO) {
        if (!LanAddressValidator.isAllowedTargetHost(host)) {
            Log.e(TAG, "Rejected unauthorized target host: $host")
            return@withContext false
        }

        val buttonId = mapActionToButtonId(action)
        val pressPacket = createButtonPacket(buttonId, isPressed = true)
        val releasePacket = createButtonPacket(buttonId, isPressed = false)

        mutex.withLock {
            try {
                val socket = getOrCreateSocket(host, port)
                val out: OutputStream = socket.getOutputStream()
                out.write(pressPacket)
                out.flush()

                delay(25L) // Brief pulse duration

                out.write(releasePacket)
                out.flush()
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send OpenBikeControl action to $host:$port", e)
                closeSocket()
                return@withContext false
            }
        }
    }

    private fun getOrCreateSocket(host: String, port: Int): Socket {
        val current = activeSocket
        if (current != null && current.isConnected && !current.isClosed && activeHost == host && activePort == port) {
            return current
        }
        closeSocket()

        val socket = socketFactory()
        socket.tcpNoDelay = true
        socket.soTimeout = SOCKET_TIMEOUT_MS
        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

        activeSocket = socket
        activeHost = host
        activePort = port
        return socket
    }

    fun close() {
        closeSocket()
    }

    private fun closeSocket() {
        try {
            activeSocket?.close()
        } catch (_: Exception) {
        }
        activeSocket = null
        activeHost = null
        activePort = -1
    }
}

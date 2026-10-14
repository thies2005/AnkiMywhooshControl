package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.MyWhooshAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.Socket

class OpenBikeControlClientTest {

    @Test
    fun testActionOpcodes() {
        assertEquals(0x01.toByte(), MyWhooshAction.SHIFT_UP.openBikeOpcode)
        assertEquals(0x02.toByte(), MyWhooshAction.SHIFT_DOWN.openBikeOpcode)
        assertEquals(0x18.toByte(), MyWhooshAction.STEER_LEFT.openBikeOpcode)
        assertEquals(0x19.toByte(), MyWhooshAction.STEER_RIGHT.openBikeOpcode)
        assertEquals(0x14.toByte(), MyWhooshAction.UI_CONFIRM.openBikeOpcode)
        assertEquals(0x15.toByte(), MyWhooshAction.UI_BACK.openBikeOpcode)
        assertEquals(0x40.toByte(), MyWhooshAction.TOGGLE_CAMERA.openBikeOpcode)
        assertEquals(0x44.toByte(), MyWhooshAction.HUD_TOGGLE.openBikeOpcode)
        assertEquals(0x20.toByte(), MyWhooshAction.EMOTE.openBikeOpcode)
        assertEquals(0x24.toByte(), MyWhooshAction.SCREENSHOT.openBikeOpcode)
        assertEquals(0x50.toByte(), MyWhooshAction.USE_POWERUP.openBikeOpcode)
    }

    @Test
    fun testServerLifecycleAndActionBroadcast() = runBlocking(Dispatchers.IO) {
        val server = OpenBikeControlClient()
        server.startServer(0) // dynamic port

        // Wait a moment for server to bind
        delay(100L)

        // Connect a test client socket
        var clientPort = 0
        // Find bound port via reflection or testing
        val srvField = server.javaClass.getDeclaredField("serverSocket")
        srvField.isAccessible = true
        val srvSocket = srvField.get(server) as? java.net.ServerSocket
        if (srvSocket != null) {
            clientPort = srvSocket.localPort
        }

        assertTrue(clientPort > 0)

        val socket = Socket("127.0.0.1", clientPort)
        val input: InputStream = socket.getInputStream()

        // Expect initial 3 bytes device status: [0x02, 0x64, 0x01]
        val statusBuf = ByteArray(3)
        var totalRead = 0
        while (totalRead < 3) {
            val r = input.read(statusBuf, totalRead, 3 - totalRead)
            if (r < 0) break
            totalRead += r
        }
        assertArrayEquals(byteArrayOf(0x02, 0x64, 0x01), statusBuf)

        assertTrue(server.hasConnectedClients())

        // Send SHIFT_UP action
        val delivered = server.sendAction("127.0.0.1", clientPort, MyWhooshAction.SHIFT_UP)
        assertTrue(delivered)

        // Client should receive 6 bytes: [0x01, 0x01, 0x01] then [0x01, 0x01, 0x00]
        val actionBuf = ByteArray(6)
        totalRead = 0
        while (totalRead < 6) {
            val r = input.read(actionBuf, totalRead, 6 - totalRead)
            if (r < 0) break
            totalRead += r
        }
        assertArrayEquals(
            byteArrayOf(0x01, 0x01, 0x01, 0x01, 0x01, 0x00),
            actionBuf
        )

        socket.close()
        server.stopServer()
    }

    @Test
    fun testSendActionRejectsPublicIp() = runBlocking {
        val server = OpenBikeControlClient()
        val success = server.sendAction("8.8.8.8", 21587, MyWhooshAction.SHIFT_UP)
        assertFalse(success)
    }
}

package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.MyWhooshAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket

class OpenBikeControlClientTest {

    @Test
    fun testButtonMappingAndPacketFormat() {
        assertEquals(0x01.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.SHIFT_UP))
        assertEquals(0x02.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.SHIFT_DOWN))
        assertEquals(0x18.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.STEER_LEFT))
        assertEquals(0x19.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.STEER_RIGHT))
        assertEquals(0x14.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.SELECT))
        assertEquals(0x21.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.BACK))
        assertEquals(0x30.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.TOGGLE_UI))
        assertEquals(0x31.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.EMOTE))
        assertEquals(0x32.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.CAMERA_VIEW))
        assertEquals(0x33.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.POWER_UP))
        assertEquals(0x34.toByte(), OpenBikeControlClient.mapActionToButtonId(MyWhooshAction.SCREENSHOT))

        val pressPacket = OpenBikeControlClient.createButtonPacket(0x01.toByte(), isPressed = true)
        assertArrayEquals(byteArrayOf(0x01, 0x01, 0x01), pressPacket)

        val releasePacket = OpenBikeControlClient.createButtonPacket(0x01.toByte(), isPressed = false)
        assertArrayEquals(byteArrayOf(0x01, 0x01, 0x00), releasePacket)
    }

    @Test
    fun testSendActionToMockServer() = runBlocking(Dispatchers.IO) {
        val server = ServerSocket(0)
        val port = server.localPort
        val client = OpenBikeControlClient()

        val serverDeferred = async {
            val clientSocket: Socket = server.accept()
            val input: InputStream = clientSocket.getInputStream()
            val buf = ByteArray(6)
            var totalRead = 0
            while (totalRead < 6) {
                val r = input.read(buf, totalRead, 6 - totalRead)
                if (r < 0) break
                totalRead += r
            }
            clientSocket.close()
            buf
        }

        val success = client.sendAction("127.0.0.1", port, MyWhooshAction.SHIFT_UP)
        assertTrue(success)

        val received = serverDeferred.await()
        server.close()
        client.close()

        // Expect: [0x01, 0x01, 0x01] followed by [0x01, 0x01, 0x00]
        assertArrayEquals(
            byteArrayOf(0x01, 0x01, 0x01, 0x01, 0x01, 0x00),
            received
        )
    }

    @Test
    fun testSendActionRejectsPublicIp() = runBlocking {
        val client = OpenBikeControlClient()
        val success = client.sendAction("8.8.8.8", 21587, MyWhooshAction.SHIFT_UP)
        assertFalse(success)
        client.close()
    }
}

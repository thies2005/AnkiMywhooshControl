package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.MyWhooshAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket

class OpenBikeControlClientTest {

    private class NoOpMdnsAdvertiser : DesktopMdnsAdvertiser() {
        override fun registerOpenBikeService(port: Int) {}
        override fun unregister() {}
        override fun close() {}
    }

    private lateinit var client: OpenBikeControlClient

    @Before
    fun setUp() {
        client = OpenBikeControlClient(mdnsAdvertiser = NoOpMdnsAdvertiser())
    }

    @After
    fun tearDown() {
        client.stopServer()
    }

    private suspend fun awaitServerPort(server: OpenBikeControlClient, timeoutMs: Long = 2000L): Int {
        val field = OpenBikeControlClient::class.java.getDeclaredField("serverSocket")
        field.isAccessible = true
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val srv = field.get(server) as? ServerSocket
            if (srv != null && srv.localPort > 0) {
                return srv.localPort
            }
            delay(25L)
        }
        error("Timed out waiting for OpenBikeControl server to bind port")
    }

    private fun readExact(input: InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var total = 0
        while (total < length) {
            val r = input.read(buf, total, length - total)
            if (r < 0) break
            total += r
        }
        assertEquals(length, total)
        return buf
    }

    @Test
    fun testActionOpcodes() {
        assertEquals(0x01.toByte(), MyWhooshAction.SHIFT_UP.openBikeOpcode)
        assertEquals(0x02.toByte(), MyWhooshAction.SHIFT_DOWN.openBikeOpcode)
        assertEquals(0x10.toByte(), MyWhooshAction.UI_NAV_UP.openBikeOpcode)
        assertEquals(0x11.toByte(), MyWhooshAction.UI_NAV_DOWN.openBikeOpcode)
        assertEquals(0x12.toByte(), MyWhooshAction.UI_NAV_LEFT.openBikeOpcode)
        assertEquals(0x13.toByte(), MyWhooshAction.UI_NAV_RIGHT.openBikeOpcode)
        assertEquals(0x14.toByte(), MyWhooshAction.UI_CONFIRM.openBikeOpcode)
        assertEquals(0x15.toByte(), MyWhooshAction.UI_BACK.openBikeOpcode)
        assertEquals(0x18.toByte(), MyWhooshAction.STEER_LEFT.openBikeOpcode)
        assertEquals(0x19.toByte(), MyWhooshAction.STEER_RIGHT.openBikeOpcode)
        assertEquals(0x20.toByte(), MyWhooshAction.EMOTE.openBikeOpcode)
        assertEquals(0x24.toByte(), MyWhooshAction.SCREENSHOT.openBikeOpcode)
        assertEquals(0x40.toByte(), MyWhooshAction.TOGGLE_CAMERA.openBikeOpcode)
        assertEquals(0x44.toByte(), MyWhooshAction.HUD_TOGGLE.openBikeOpcode)
        assertEquals(0x50.toByte(), MyWhooshAction.USE_POWERUP.openBikeOpcode)
    }

    @Test
    fun testServerLifecycleAndHandshakeAndDispatches() = runBlocking(Dispatchers.IO) {
        client.startServer(0)
        val port = awaitServerPort(client)
        assertTrue(port > 0)

        assertFalse(client.hasConnectedClients())
        assertEquals(0, client.connectedClientCount())

        val socket = Socket("127.0.0.1", port)
        socket.soTimeout = 3000
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        // 1. Handshake: Server sends 0x02 Device Status [0x02, 0x64, 0x01] upon connect
        val handshake = readExact(input, 3)
        assertArrayEquals(byteArrayOf(0x02, 0x64, 0x01), handshake)

        delay(50L)
        assertTrue(client.hasConnectedClients())
        assertEquals(1, client.connectedClientCount())

        // 2. Test SHIFT_UP action: press + 90ms hold + release
        val deliveredShiftUp = client.sendAction("127.0.0.1", port, MyWhooshAction.SHIFT_UP)
        assertTrue(deliveredShiftUp)
        val shiftUpBytes = readExact(input, 6)
        assertArrayEquals(byteArrayOf(0x01, 0x01, 0x01, 0x01, 0x01, 0x00), shiftUpBytes)

        // 3. Test SHIFT_DOWN action
        val deliveredShiftDown = client.sendAction("127.0.0.1", port, MyWhooshAction.SHIFT_DOWN)
        assertTrue(deliveredShiftDown)
        val shiftDownBytes = readExact(input, 6)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x01, 0x01, 0x02, 0x00), shiftDownBytes)

        // 4. Test STEER_LEFT (0x18), STEER_RIGHT (0x19), EMOTE (0x20)
        assertTrue(client.sendAction("127.0.0.1", port, MyWhooshAction.STEER_LEFT))
        assertArrayEquals(byteArrayOf(0x01, 0x18, 0x01, 0x01, 0x18, 0x00), readExact(input, 6))

        assertTrue(client.sendAction("127.0.0.1", port, MyWhooshAction.STEER_RIGHT))
        assertArrayEquals(byteArrayOf(0x01, 0x19, 0x01, 0x01, 0x19, 0x00), readExact(input, 6))

        assertTrue(client.sendAction("127.0.0.1", port, MyWhooshAction.EMOTE))
        assertArrayEquals(byteArrayOf(0x01, 0x20, 0x01, 0x01, 0x20, 0x00), readExact(input, 6))

        // 5. Test 0x04 AppInfo packet parsing
        // Format: [0x04, ver=1, idLen=8, "MyWhoosh", verLen=5, "3.4.1", btnCount=2, 0x01, 0x02]
        val appIdBytes = "MyWhoosh".toByteArray(Charsets.UTF_8)
        val appVerBytes = "3.4.1".toByteArray(Charsets.UTF_8)
        val appInfoPacket = byteArrayOf(
            0x04,
            0x01,
            appIdBytes.size.toByte(),
            *appIdBytes,
            appVerBytes.size.toByte(),
            *appVerBytes,
            0x02,
            0x01,
            0x02
        )
        output.write(appInfoPacket)
        output.flush()

        delay(100L)
        assertEquals("MyWhoosh v3.4.1", client.connectedAppInfo.value)

        // 6. Test 0x03 Haptic ACK packet handling
        val hapticAckPacket = byteArrayOf(0x03, 0x01, 0x02)
        output.write(hapticAckPacket)
        output.flush()
        delay(50L)

        // 7. Test client disconnect
        socket.close()
        delay(100L)

        assertEquals(0, client.connectedClientCount())
        assertFalse(client.hasConnectedClients())
        assertNull(client.connectedAppInfo.value)
    }

    @Test
    fun testSendActionRejectsPublicIp() = runBlocking {
        val success = client.sendAction("8.8.8.8", 21587, MyWhooshAction.SHIFT_UP)
        assertFalse(success)
    }
}

package com.ankiride.bridge.ble

import com.ankiride.bridge.core.BleConnectionState
import com.ankiride.bridge.core.ButtonGestureEvent
import com.ankiride.bridge.core.ClickButton
import com.ankiride.bridge.core.PressType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method

class DesktopZwiftBleControllerTest {

    private lateinit var testScope: CoroutineScope
    private lateinit var controller: DesktopZwiftBleController

    private fun encodeVarint(value: Int): ByteArray {
        val out = mutableListOf<Byte>()
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                out.add((v and 0x7F).toByte())
                break
            } else {
                out.add(((v and 0x7F) or 0x80).toByte())
                v = v ushr 7
            }
        }
        return out.toByteArray()
    }

    private fun buildPacket(buttonMapWire: Int): ByteArray {
        val varintBytes = encodeVarint(buttonMapWire)
        val packet = ByteArray(2 + varintBytes.size)
        packet[0] = ZwiftPacketParser.MSG_KEYPAD_STATUS // 0x23
        packet[1] = ZwiftPacketParser.PROTO_FIELD_1_VARINT // 0x08
        System.arraycopy(varintBytes, 0, packet, 2, varintBytes.size)
        return packet
    }

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        controller = DesktopZwiftBleController(scope = testScope)
    }

    @After
    fun tearDown() {
        controller.disconnect()
        testScope.cancel()
    }

    @Test
    fun testHandleRawNotificationSinglePod() {
        val macRight = "AA:BB:CC:DD:EE:01"
        // Physical packet for Z button pressed: [23 08 FF FE FF FF 0F]
        val packetZ = byteArrayOf(
            0x23, 0x08,
            0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F
        )

        controller.handleRawNotification(macRight, packetZ)

        assertEquals(ClickButton.Z.bitmask, controller.podActiveMasks[macRight])
    }

    @Test
    fun testIdleLeftPodDoesNotOverwriteActiveRightPod() {
        val macRight = "AA:BB:CC:DD:EE:01"
        val macLeft = "AA:BB:CC:DD:EE:02"

        // 1. Right Pod presses and holds Z (Top Orange)
        val packetZ = byteArrayOf(
            0x23, 0x08,
            0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F
        )
        controller.handleRawNotification(macRight, packetZ, nowMs = 1000L)
        assertEquals(ClickButton.Z.bitmask, controller.podActiveMasks[macRight])

        // 2. Idle Left Pod sends all-released packet: [23 08 FF FF FF FF 0F]
        val packetAllReleased = byteArrayOf(
            0x23, 0x08,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F
        )
        controller.handleRawNotification(macLeft, packetAllReleased, nowMs = 1020L)

        // Verify Left Pod active mask is 0, but Right Pod active mask remains Z
        assertEquals(0, controller.podActiveMasks[macLeft])
        assertEquals(ClickButton.Z.bitmask, controller.podActiveMasks[macRight])

        // 3. Right Pod holds SHIFT_UP_RIGHT (+ Rocker: wire = ~(0x1000))
        val packetShiftUp = buildPacket(ClickButton.SHIFT_UP_RIGHT.bitmask.inv())
        controller.handleRawNotification(macRight, packetShiftUp, nowMs = 1050L)
        assertEquals(ClickButton.SHIFT_UP_RIGHT.bitmask, controller.podActiveMasks[macRight])

        // Left Pod sends all-released again
        controller.handleRawNotification(macLeft, packetAllReleased, nowMs = 1070L)
        assertEquals(0, controller.podActiveMasks[macLeft])
        assertEquals(ClickButton.SHIFT_UP_RIGHT.bitmask, controller.podActiveMasks[macRight])
    }

    @Test
    fun testDualPodSimultaneousButtonsBothActive() {
        val macRight = "AA:BB:CC:DD:EE:01"
        val macLeft = "AA:BB:CC:DD:EE:02"

        // Right Pod holds B (0x0020)
        val packetB = buildPacket(ClickButton.B.bitmask.inv())
        controller.handleRawNotification(macRight, packetB, nowMs = 1000L)

        // Left Pod holds D-pad LEFT (0x0001)
        val packetLeft = buildPacket(ClickButton.LEFT.bitmask.inv())
        controller.handleRawNotification(macLeft, packetLeft, nowMs = 1010L)

        assertEquals(ClickButton.B.bitmask, controller.podActiveMasks[macRight])
        assertEquals(ClickButton.LEFT.bitmask, controller.podActiveMasks[macLeft])
    }

    @Test
    fun testHeartbeatPacketsFiltered() {
        val mac = "AA:BB:CC:DD:EE:01"

        // Periodic keepalive 0x15
        controller.handleRawNotification(mac, byteArrayOf(0x15, 0x01, 0x02))
        assertNull(controller.podActiveMasks[mac])

        // Periodic keepalive 0x19
        controller.handleRawNotification(mac, byteArrayOf(0x19, 0x00))
        assertNull(controller.podActiveMasks[mac])

        // Empty packet
        controller.handleRawNotification(mac, byteArrayOf())
        assertNull(controller.podActiveMasks[mac])
    }

    @Test
    fun testEmitSimulatedGestureAllButtons() = runBlocking {
        val receivedEvents = mutableListOf<ButtonGestureEvent>()
        val collectorJob = launch(Dispatchers.Default) {
            controller.gestureEvents.collect { receivedEvents.add(it) }
        }
        delay(150L)

        val testButtons = ClickButton.entries
        for (button in testButtons) {
            controller.emitSimulatedGesture(button, PressType.SINGLE_CLICK)
            delay(5L)
            controller.emitSimulatedGesture(button, PressType.LONG_PRESS)
            delay(5L)
        }

        val start = System.currentTimeMillis()
        while (receivedEvents.size < testButtons.size * 2 && System.currentTimeMillis() - start < 3000L) {
            delay(20L)
        }
        collectorJob.cancel()

        assertEquals(testButtons.size * 2, receivedEvents.size)

        var idx = 0
        for (button in testButtons) {
            assertEquals(button, receivedEvents[idx].button)
            assertEquals(PressType.SINGLE_CLICK, receivedEvents[idx].type)
            idx++

            assertEquals(button, receivedEvents[idx].button)
            assertEquals(PressType.LONG_PRESS, receivedEvents[idx].type)
            idx++
        }
    }

    @Test
    fun testZwiftBleWorkerResourceExistsAndCompiles() {
        val stream = javaClass.getResourceAsStream("/ble/zwift_ble_worker.py")
        assertNotNull("Resource /ble/zwift_ble_worker.py must exist on classpath", stream)

        val bytes = stream!!.readBytes()
        assertTrue("Resource /ble/zwift_ble_worker.py must not be empty", bytes.isNotEmpty())

        val tempFile = File.createTempFile("zwift_ble_worker_test", ".py")
        try {
            FileOutputStream(tempFile).use { it.write(bytes) }

            // Verify with python3 -m py_compile
            val pb = ProcessBuilder("python3", "-m", "py_compile", tempFile.absolutePath)
            val proc = pb.start()
            val finished = proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue("python3 py_compile should complete within 5 seconds", finished)
            assertEquals("python3 -m py_compile must exit with code 0 (valid Python syntax)", 0, proc.exitValue())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testParseWorkerLineStateTransitions() {
        val parseWorkerLineMethod: Method = DesktopZwiftBleController::class.java.getDeclaredMethod(
            "parseWorkerLine",
            String::class.java
        )
        parseWorkerLineMethod.isAccessible = true

        fun parse(line: String) {
            parseWorkerLineMethod.invoke(controller, line)
        }

        parse("STATE SCANNING")
        assertEquals(BleConnectionState.SCANNING, controller.connectionState.value)

        parse("STATE CONNECTING")
        assertEquals(BleConnectionState.CONNECTING, controller.connectionState.value)

        parse("STATE HANDSHAKING")
        assertEquals(BleConnectionState.HANDSHAKING, controller.connectionState.value)

        parse("STATE CONNECTED_RIGHT_ONLY AA:BB:CC:DD:EE:01")
        assertEquals(BleConnectionState.CONNECTED_RIGHT_ONLY, controller.connectionState.value)
        assertEquals("AA:BB:CC:DD:EE:01", controller.connectedRightMac.value)

        parse("STATE CONNECTED_BOTH_PODS AA:BB:CC:DD:EE:02")
        assertEquals(BleConnectionState.CONNECTED_BOTH_PODS, controller.connectionState.value)
        assertEquals("AA:BB:CC:DD:EE:02", controller.connectedLeftMac.value)

        // PACKET line parsed
        parse("PACKET AA:BB:CC:DD:EE:01 2308FFFEFFFF0F")
        assertEquals(ClickButton.Z.bitmask, controller.podActiveMasks["AA:BB:CC:DD:EE:01"])

        // Disconnect left pod
        parse("STATE DISCONNECTED AA:BB:CC:DD:EE:02")
        assertNull(controller.connectedLeftMac.value)
        assertEquals(BleConnectionState.CONNECTED_RIGHT_ONLY, controller.connectionState.value)

        // Disconnect right pod
        parse("STATE DISCONNECTED AA:BB:CC:DD:EE:01")
        assertNull(controller.connectedRightMac.value)
        assertEquals(BleConnectionState.DISCONNECTED, controller.connectionState.value)

        // ERROR line
        parse("STATE ERROR bleak_not_installed")
        assertEquals(BleConnectionState.ERROR, controller.connectionState.value)
    }

    @Test
    fun testDisconnectResetsControllerState() {
        controller.handleRawNotification(
            "AA:BB:CC:DD:EE:01",
            byteArrayOf(0x23, 0x08, 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F)
        )
        assertEquals(1, controller.podActiveMasks.size)

        controller.disconnect()

        assertEquals(0, controller.podActiveMasks.size)
        assertNull(controller.connectedRightMac.value)
        assertNull(controller.connectedLeftMac.value)
        assertEquals(BleConnectionState.DISCONNECTED, controller.connectionState.value)
    }
}

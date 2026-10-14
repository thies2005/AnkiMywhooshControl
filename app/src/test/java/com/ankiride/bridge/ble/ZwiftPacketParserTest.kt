package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ClickButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ZwiftPacketParserTest {

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

    @Test
    fun testValidButtonPressSingleButton() {
        // Wire format: bit set to 0 means pressed, 1 means released.
        // If button A (0x0010) is pressed, buttonMap in wire format is (~0x0010).
        val activeExpected = ClickButton.A.bitmask
        val wireValue = activeExpected.inv()
        val packet = buildPacket(wireValue)

        val parsedMask = ZwiftPacketParser.parseActiveButtonMask(packet)
        assertNotNull(parsedMask)
        assertEquals(activeExpected, parsedMask)

        val buttons = ZwiftPacketParser.getPressedButtons(parsedMask!!)
        assertEquals(setOf(ClickButton.A), buttons)
    }

    @Test
    fun testValidMultipleButtonsPressed() {
        val activeExpected = ClickButton.SHIFT_UP_RIGHT.bitmask or ClickButton.Z.bitmask
        val wireValue = activeExpected.inv()
        val packet = buildPacket(wireValue)

        val parsedMask = ZwiftPacketParser.parseActiveButtonMask(packet)
        assertNotNull(parsedMask)
        assertEquals(activeExpected, parsedMask)

        val buttons = ZwiftPacketParser.getPressedButtons(parsedMask!!)
        assertEquals(setOf(ClickButton.SHIFT_UP_RIGHT, ClickButton.Z), buttons)
    }

    @Test
    fun testNoButtonsPressed() {
        // When no button is pressed, wire value has all 1s (0xFFFFFFFF or ~0).
        val wireValue = 0.inv()
        val packet = buildPacket(wireValue)

        val parsedMask = ZwiftPacketParser.parseActiveButtonMask(packet)
        assertNotNull(parsedMask)
        assertEquals(0, parsedMask)

        val buttons = ZwiftPacketParser.getPressedButtons(parsedMask!!)
        assertTrue(buttons.isEmpty())
    }

    @Test
    fun testPacketTooShort() {
        assertNull(ZwiftPacketParser.parseActiveButtonMask(byteArrayOf()))
        assertNull(ZwiftPacketParser.parseActiveButtonMask(byteArrayOf(0x23)))
        assertNull(ZwiftPacketParser.parseActiveButtonMask(byteArrayOf(0x23, 0x08)))
    }

    @Test
    fun testPacketTooLong() {
        val oversized = ByteArray(65) { 0x23 }
        oversized[1] = 0x08
        assertNull(ZwiftPacketParser.parseActiveButtonMask(oversized))
    }

    @Test
    fun testWrongMessageId() {
        val packet = byteArrayOf(0x24, 0x08, 0x00)
        assertNull(ZwiftPacketParser.parseActiveButtonMask(packet))
    }

    @Test
    fun testWrongProtoField() {
        val packet = byteArrayOf(0x23, 0x10, 0x00)
        assertNull(ZwiftPacketParser.parseActiveButtonMask(packet))
    }

    @Test
    fun testVarintOverflowGuard() {
        // Varint with 6 continuation bytes (exceeds MAX_VARINT_BYTES = 5)
        val packet = byteArrayOf(
            0x23, 0x08,
            0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01.toByte()
        )
        assertNull(ZwiftPacketParser.parseActiveButtonMask(packet))
    }

    @Test
    fun testTruncatedVarint() {
        // Continuation bit set on the last byte
        val packet = byteArrayOf(0x23, 0x08, 0x80.toByte())
        assertNull(ZwiftPacketParser.parseActiveButtonMask(packet))
    }

    @Test
    fun testSecurityGateSG1FuzzingTenThousandPayloads() {
        // SG-1 requirement: fuzz 10,000 random/malformed byte arrays with zero crashes or exceptions
        val random = Random(42)
        for (i in 0 until 10_000) {
            val length = random.nextInt(0, 100)
            val payload = ByteArray(length)
            random.nextBytes(payload)

            // Must not throw any exception
            val result = ZwiftPacketParser.parseActiveButtonMask(payload)
            if (result != null) {
                // If by pure chance it formed a valid packet, getPressedButtons must not crash either
                val buttons = ZwiftPacketParser.getPressedButtons(result)
                assertNotNull(buttons)
            }
        }
    }
}

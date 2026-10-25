package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ClickButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Wire format: active-low. Bit 0 means pressed, 1 means released.
        // If button A (0x0010) is pressed, buttonMap in wire format is (~0x0010).
        val activeExpected = ClickButton.A.bitmask
        val wireValue = activeExpected.inv()
        val packet = buildPacket(wireValue)

        val parsedMask = ZwiftPacketParser.parseActiveButtonMask(packet)
        assertNotNull(parsedMask)
        assertEquals(activeExpected, parsedMask)

        val buttonMap = ZwiftPacketParser.parse(packet)
        assertTrue(buttonMap[ClickButton.A] == true)
        assertTrue(buttonMap[ClickButton.B] == false)
        assertTrue(buttonMap[ClickButton.Z] == false)
    }

    @Test
    fun testValidMultipleButtonsPressed() {
        val activeExpected = ClickButton.SHIFT_UP_RIGHT.bitmask or ClickButton.Z.bitmask
        val wireValue = activeExpected.inv()
        val packet = buildPacket(wireValue)

        val parsedMask = ZwiftPacketParser.parseActiveButtonMask(packet)
        assertNotNull(parsedMask)
        assertEquals(activeExpected, parsedMask)

        val buttonMap = ZwiftPacketParser.parse(packet)
        assertTrue(buttonMap[ClickButton.SHIFT_UP_RIGHT] == true)
        assertTrue(buttonMap[ClickButton.Z] == true)
        assertTrue(buttonMap[ClickButton.A] == false)
        assertTrue(buttonMap[ClickButton.B] == false)
    }

    @Test
    fun testAllFourColorfulButtonsPressed() {
        val activeExpected = ClickButton.Z.bitmask or ClickButton.A.bitmask or ClickButton.Y.bitmask or ClickButton.B.bitmask
        val wireValue = activeExpected.inv()
        val packet = buildPacket(wireValue)

        val parsedMask = ZwiftPacketParser.parseActiveButtonMask(packet)
        assertEquals(activeExpected, parsedMask)

        val buttonMap = ZwiftPacketParser.parse(packet)
        assertTrue(buttonMap[ClickButton.Z] == true)
        assertTrue(buttonMap[ClickButton.A] == true)
        assertTrue(buttonMap[ClickButton.Y] == true)
        assertTrue(buttonMap[ClickButton.B] == true)
        assertFalse(buttonMap[ClickButton.SHIFT_UP_RIGHT] == true)
    }

    @Test
    fun testPhysicalZButtonPacketFromTabletLog() {
        // Exact packet logged from physical Zwift Click v2 when pressing Z:
        // [23 08 FF FE FF FF 0F] -> activeMask=0x00080 (Bit 7)
        val packet = byteArrayOf(
            0x23, 0x08,
            0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F
        )
        val parsedMask = ZwiftPacketParser.parseActiveButtonMask(packet)
        assertNotNull(parsedMask)
        assertEquals(ClickButton.Z.bitmask, parsedMask)

        val buttonMap = ZwiftPacketParser.parse(packet)
        assertTrue(buttonMap[ClickButton.Z] == true)
        assertTrue(buttonMap[ClickButton.A] == false)
        assertTrue(buttonMap[ClickButton.B] == false)
        assertTrue(buttonMap[ClickButton.Y] == false)
    }

    @Test
    fun testLegacy0x37ShiftPackets() {
        // Legacy 0x37 Click packets
        val shiftUpPacket = byteArrayOf(0x37, 0x08, 0x00)
        val shiftUpMask = ZwiftPacketParser.parseActiveButtonMask(shiftUpPacket)
        assertEquals(ClickButton.SHIFT_UP_RIGHT.bitmask, shiftUpMask)

        val shiftDownPacket = byteArrayOf(0x37, 0x08, 0x01)
        val shiftDownMask = ZwiftPacketParser.parseActiveButtonMask(shiftDownPacket)
        assertEquals(ClickButton.SHIFT_DOWN_LEFT.bitmask, shiftDownMask)

        val unknownShiftPacket = byteArrayOf(0x37, 0x08, 0x02)
        val unknownShiftMask = ZwiftPacketParser.parseActiveButtonMask(unknownShiftPacket)
        assertEquals(0, unknownShiftMask)
    }

    @Test
    fun testNoButtonsPressed() {
        // When no button is pressed, wire value has all 1s (0xFFFFFFFF or ~0).
        val wireValue = 0.inv()
        val packet = buildPacket(wireValue)

        val parsedMask = ZwiftPacketParser.parseActiveButtonMask(packet)
        assertNotNull(parsedMask)
        assertEquals(0, parsedMask)

        val buttonMap = ZwiftPacketParser.parse(packet)
        assertTrue(buttonMap.values.none { it })
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
    fun testExactMaxVarintBytes() {
        // 5 bytes varint: 4 continuation bytes + 1 terminating byte
        val packet = byteArrayOf(
            0x23, 0x08,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F.toByte()
        )
        val mask = ZwiftPacketParser.parseActiveButtonMask(packet)
        assertNotNull(mask)
        assertEquals(0, mask) // all released (~0xFFFFFFFF & 32-bit int)
    }

    @Test
    fun testTruncatedVarint() {
        // Continuation bit set on the last byte
        val packet = byteArrayOf(0x23, 0x08, 0x80.toByte())
        assertNull(ZwiftPacketParser.parseActiveButtonMask(packet))
    }

    @Test
    fun testField2AnalogPaddlesDeflection() {
        // Field 1: All released [23 08 FF FF FF FF 0F]
        // Field 2 (0x12): RideAnalogKeyGroup
        // [0x12] [groupLen = 6]
        // entry: [0x0A, entryLen = 4, 0x08, locId = 0, 0x10, rawVal = 0x25] -> SHIFT_DOWN_LEFT
        val packetLoc0 = byteArrayOf(
            0x23, 0x08, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F,
            0x12, 0x06,
            0x0A, 0x04, 0x08, 0x00, 0x10, 0x25
        )
        val maskLoc0 = ZwiftPacketParser.parseActiveButtonMask(packetLoc0)
        assertNotNull(maskLoc0)
        assertEquals(ClickButton.SHIFT_DOWN_LEFT.bitmask, maskLoc0)

        // locId = 1 -> SHIFT_UP_RIGHT
        val packetLoc1 = byteArrayOf(
            0x23, 0x08, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F,
            0x12, 0x06,
            0x0A, 0x04, 0x08, 0x01, 0x10, 0x30
        )
        val maskLoc1 = ZwiftPacketParser.parseActiveButtonMask(packetLoc1)
        assertNotNull(maskLoc1)
        assertEquals(ClickButton.SHIFT_UP_RIGHT.bitmask, maskLoc1)

        // Both loc 0 and loc 1 in one group
        val packetBothLocs = byteArrayOf(
            0x23, 0x08, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F,
            0x12, 0x0C,
            0x0A, 0x04, 0x08, 0x00, 0x10, 0x20,
            0x0A, 0x04, 0x08, 0x01, 0x10, 0x40
        )
        val maskBoth = ZwiftPacketParser.parseActiveButtonMask(packetBothLocs)
        val expectedBoth = ClickButton.SHIFT_DOWN_LEFT.bitmask or ClickButton.SHIFT_UP_RIGHT.bitmask
        assertEquals(expectedBoth, maskBoth)

        // rawVal == 0 should NOT set paddle mask
        val packetZeroVal = byteArrayOf(
            0x23, 0x08, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F,
            0x12, 0x06,
            0x0A, 0x04, 0x08, 0x01, 0x10, 0x00
        )
        val maskZero = ZwiftPacketParser.parseActiveButtonMask(packetZeroVal)
        assertEquals(0, maskZero)
    }

    @Test
    fun testField2MalformedPaddlesHandledGracefully() {
        // Field 2 truncated length
        val truncatedField2 = byteArrayOf(
            0x23, 0x08, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F,
            0x12, 0x10, 0x0A
        )
        val maskTruncated = ZwiftPacketParser.parseActiveButtonMask(truncatedField2)
        assertEquals(0, maskTruncated)

        // Field 2 invalid entry header (not 0x0A)
        val invalidHeader = byteArrayOf(
            0x23, 0x08, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F,
            0x12, 0x06,
            0x0B, 0x04, 0x08, 0x01, 0x10, 0x30
        )
        val maskInvalidHeader = ZwiftPacketParser.parseActiveButtonMask(invalidHeader)
        assertEquals(0, maskInvalidHeader)
    }

    @Test
    fun testParseReturnsMapWithAllButtons() {
        val packet = byteArrayOf(0x37, 0x08, 0x00) // SHIFT_UP_RIGHT
        val map = ZwiftPacketParser.parse(packet)
        assertEquals(ClickButton.entries.size, map.size)
        assertTrue(map[ClickButton.SHIFT_UP_RIGHT] == true)
        assertTrue(map[ClickButton.SHIFT_DOWN_LEFT] == false)

        val invalidMap = ZwiftPacketParser.parse(byteArrayOf())
        assertEquals(ClickButton.entries.size, invalidMap.size)
        assertTrue(invalidMap.values.none { it })
    }

    @Test
    fun testSecurityFuzzingTenThousandPayloads() {
        val random = Random(42)
        for (i in 0 until 10_000) {
            val length = random.nextInt(0, 100)
            val payload = ByteArray(length)
            random.nextBytes(payload)

            // Must not throw any exception
            val result = ZwiftPacketParser.parseActiveButtonMask(payload)
            if (result != null) {
                val buttonMap = ZwiftPacketParser.parse(payload)
                assertNotNull(buttonMap)
            }
        }
    }
}

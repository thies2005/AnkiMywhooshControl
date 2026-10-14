package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ClickButton

object ZwiftPacketParser {
    const val MSG_KEYPAD_STATUS: Byte = 0x23
    const val PROTO_FIELD_1_VARINT: Byte = 0x08
    const val MAX_PACKET_SIZE = 64
    const val MAX_VARINT_BYTES = 5

    /**
     * Returns the active (pressed) button mask where 1 = pressed, or null if not a valid 0x23 packet.
     * Inverse logic: in the wire format, 0 means pressed and 1 means released.
     * Computes result.inv() so that returned integer has 1 for pressed buttons.
     */
    fun parseActiveButtonMask(payload: ByteArray): Int? {
        if (payload.size < 3 || payload.size > MAX_PACKET_SIZE) return null
        if (payload[0] != MSG_KEYPAD_STATUS || payload[1] != PROTO_FIELD_1_VARINT) return null

        var result = 0
        var shift = 0
        var index = 2
        var bytesRead = 0

        while (index < payload.size) {
            if (bytesRead >= MAX_VARINT_BYTES) return null // Malformed varint overflow guard
            val b = payload[index].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            bytesRead++
            index++
            if ((b and 0x80) == 0) {
                // Inverse logic: 0 bit in wire format means button is pressed
                return result.inv()
            }
            shift += 7
        }
        return null // Truncated varint
    }

    /**
     * Maps an active buttons mask to a set of pressed ClickButton enums.
     */
    fun getPressedButtons(activeMask: Int): Set<ClickButton> {
        val pressed = mutableSetOf<ClickButton>()
        for (button in ClickButton.entries) {
            if ((activeMask and button.bitmask) != 0) {
                pressed.add(button)
            }
        }
        return pressed
    }
}

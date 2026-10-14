package com.ankiride.bridge.ble

import com.ankiride.bridge.core.ClickButton
import java.util.EnumMap

object ZwiftPacketParser {
    const val MSG_KEYPAD_STATUS: Byte = 0x23
    const val PROTO_FIELD_1_VARINT: Byte = 0x08
    const val MAX_PACKET_SIZE = 64
    const val MAX_VARINT_BYTES = 5

    fun parseActiveButtonMask(payload: ByteArray): Int? {
        if (payload.size < 3 || payload.size > MAX_PACKET_SIZE) return null
        if (payload[0] != MSG_KEYPAD_STATUS || payload[1] != PROTO_FIELD_1_VARINT) return null

        var result = 0
        var shift = 0
        var index = 2
        var bytesRead = 0

        while (index < payload.size) {
            if (bytesRead >= MAX_VARINT_BYTES) return null
            val b = payload[index].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            bytesRead++
            index++
            if ((b and 0x80) == 0) {
                return result.inv()
            }
            shift += 7
        }
        return null
    }

    fun parse(data: ByteArray): Map<ClickButton, Boolean> {
        val result = EnumMap<ClickButton, Boolean>(ClickButton::class.java)
        val mask = parseActiveButtonMask(data)
        if (mask == null) {
            ClickButton.entries.forEach { result[it] = false }
            return result
        }
        ClickButton.entries.forEach { button ->
            result[button] = (mask and button.bitmask) != 0
        }
        return result
    }
}

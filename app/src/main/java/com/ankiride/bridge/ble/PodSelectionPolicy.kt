package com.ankiride.bridge.ble

import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.ClickButton
import com.ankiride.bridge.core.PodAssignment

/** Conservative assignment: unknown controllers require an explicit side selection. */
object PodSelectionPolicy {
    enum class Side { LEFT, RIGHT }

    fun sideFor(address: String, podType: Byte?, config: AppConfiguration): Side? {
        val rightPin = config.pinnedRightPodMac?.takeIf { it.isNotBlank() }
        val leftPin = config.pinnedLeftPodMac?.takeIf { it.isNotBlank() }
        if (address.equals(rightPin, true) && address.equals(leftPin, true)) return null
        val side = when {
            address.equals(rightPin, true) -> Side.RIGHT
            address.equals(leftPin, true) -> Side.LEFT
            podType == 0x07.toByte() -> Side.RIGHT
            podType == 0x08.toByte() -> Side.LEFT
            config.podAssignment == PodAssignment.LEGACY_SHARED -> Side.RIGHT
            else -> return null
        }
        if (config.podAssignment == PodAssignment.LEGACY_SHARED) {
            if ((rightPin != null || leftPin != null) && !address.equals(rightPin, true) && !address.equals(leftPin, true)) return null
        } else {
            val sidePin = if (side == Side.RIGHT) rightPin else leftPin
            if (sidePin != null && !address.equals(sidePin, true)) return null
        }
        val rightOnly = config.podAssignment == PodAssignment.RIGHT_ANKI_ONLY ||
            (config.podAssignment == PodAssignment.LEGACY_SHARED && config.rightPodOnlyNoZwift)
        return if (rightOnly && side == Side.LEFT) null else side
    }

    fun filterMask(mask: Int, side: Side, assignment: PodAssignment): Int {
        if (assignment == PodAssignment.LEGACY_SHARED) return mask
        val sideMask = ClickButton.entries.filter { it.isRightPod == (side == Side.RIGHT) }
            .fold(0) { combined, button -> combined or button.bitmask }
        return mask and sideMask
    }
}

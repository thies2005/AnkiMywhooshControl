package com.ankiride.bridge.ble

import com.ankiride.bridge.core.*
import org.junit.Assert.*
import org.junit.Test

class PodSelectionPolicyTest {
    @Test fun rightOnlyRejectsLeftAndUnidentifiedUnlessExplicitlyAssignedRight() {
        val config = AppConfiguration(podAssignment = PodAssignment.RIGHT_ANKI_ONLY)
        assertNull(PodSelectionPolicy.sideFor("left", 0x08, config))
        assertNull(PodSelectionPolicy.sideFor("unknown", 0x09, config))
        assertEquals(PodSelectionPolicy.Side.RIGHT, PodSelectionPolicy.sideFor("right", 0x07, config))
        assertEquals(PodSelectionPolicy.Side.RIGHT, PodSelectionPolicy.sideFor("unknown", 0x09, config.copy(pinnedRightPodMac = "UNKNOWN")))
    }

    @Test fun pinsConstrainEachSideAndCanOverrideAdvertisedSide() {
        val config = AppConfiguration(podAssignment = PodAssignment.RIGHT_ANKI_LEFT_BIKE, pinnedRightPodMac = "A")
        assertEquals(PodSelectionPolicy.Side.RIGHT, PodSelectionPolicy.sideFor("a", 0x08, config))
        assertNull(PodSelectionPolicy.sideFor("other", 0x07, config))
        assertEquals(PodSelectionPolicy.Side.LEFT, PodSelectionPolicy.sideFor("left", 0x08, config))
        assertNull(PodSelectionPolicy.sideFor("a", 0x07, config.copy(pinnedLeftPodMac = "A")))
    }

    @Test fun mirroredBitsCannotCrossSideInSafePresets() {
        val mask = ClickButton.Y.bitmask or ClickButton.SHIFT_DOWN_LEFT.bitmask
        assertEquals(ClickButton.Y.bitmask, PodSelectionPolicy.filterMask(mask, PodSelectionPolicy.Side.RIGHT, PodAssignment.RIGHT_ANKI_ONLY))
        assertEquals(ClickButton.SHIFT_DOWN_LEFT.bitmask, PodSelectionPolicy.filterMask(mask, PodSelectionPolicy.Side.LEFT, PodAssignment.RIGHT_ANKI_LEFT_BIKE))
        assertEquals(mask, PodSelectionPolicy.filterMask(mask, PodSelectionPolicy.Side.RIGHT, PodAssignment.LEGACY_SHARED))
    }
}

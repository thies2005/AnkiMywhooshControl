package com.ankiride.bridge.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PodSettingsTest {
    @Test fun newAndExistingSettingsMigrateSafely() {
        assertEquals(PodAssignment.RIGHT_ANKI_ONLY, ButtonMappingStore.podSettingsFrom(emptyMap<String, Any>()).first)
        assertEquals(PodAssignment.LEGACY_SHARED, ButtonMappingStore.podSettingsFrom(mapOf("operating_mode" to "MODE_3_ANKI_ONLY")).first)
    }
    @Test fun assignmentAndPinsRoundTrip() {
        val config = AppConfiguration(podAssignment = PodAssignment.RIGHT_ANKI_LEFT_BIKE, pinnedRightPodMac = " aa ", pinnedLeftPodMac = "bb")
        assertEquals(Triple(PodAssignment.RIGHT_ANKI_LEFT_BIKE, "aa", "bb"), ButtonMappingStore.podSettingsFrom(ButtonMappingStore.podSettingsFor(config)))
    }
}

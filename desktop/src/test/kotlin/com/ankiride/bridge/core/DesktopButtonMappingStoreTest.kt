package com.ankiride.bridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopButtonMappingStoreTest {

    @Test
    fun testDefaultActionForMode1TabletSplitScreen() {
        val mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN

        // Shifting
        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.Z, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.A, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_UP_RIGHT, PressType.LONG_PRESS))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_DOWN_LEFT, PressType.SINGLE_CLICK))

        // Anki buttons
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_AGAIN, ButtonMappingStore.defaultActionFor(mode, ClickButton.Y, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_UNDO, ButtonMappingStore.defaultActionFor(mode, ClickButton.Y, PressType.LONG_PRESS))
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_GOOD, ButtonMappingStore.defaultActionFor(mode, ClickButton.B, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_UNDO, ButtonMappingStore.defaultActionFor(mode, ClickButton.B, PressType.LONG_PRESS))

        // Left Pod D-Pad
        assertEquals(MappedTargetAction.MW_STEER_LEFT, ButtonMappingStore.defaultActionFor(mode, ClickButton.LEFT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_STEER_RIGHT, ButtonMappingStore.defaultActionFor(mode, ClickButton.RIGHT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_CAMERA, ButtonMappingStore.defaultActionFor(mode, ClickButton.UP, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_EMOTE, ButtonMappingStore.defaultActionFor(mode, ClickButton.DOWN, PressType.SINGLE_CLICK))
    }

    @Test
    fun testDefaultActionForMode2TabletAnkiPhoneMyWhoosh() {
        val mode = OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH

        // Mode 2 shares the same combined layout as Mode 1
        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.Z, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.A, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_AGAIN, ButtonMappingStore.defaultActionFor(mode, ClickButton.Y, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_GOOD, ButtonMappingStore.defaultActionFor(mode, ClickButton.B, PressType.SINGLE_CLICK))
    }

    @Test
    fun testDefaultActionForMode3AnkiOnly() {
        val mode = OperatingMode.MODE_3_ANKI_ONLY

        assertEquals(MappedTargetAction.ANKI_FLIP_OR_HARD, ButtonMappingStore.defaultActionFor(mode, ClickButton.Z, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_UNDO, ButtonMappingStore.defaultActionFor(mode, ClickButton.Z, PressType.LONG_PRESS))
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_GOOD, ButtonMappingStore.defaultActionFor(mode, ClickButton.A, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_EASY, ButtonMappingStore.defaultActionFor(mode, ClickButton.A, PressType.LONG_PRESS))
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_AGAIN, ButtonMappingStore.defaultActionFor(mode, ClickButton.Y, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_UNDO, ButtonMappingStore.defaultActionFor(mode, ClickButton.Y, PressType.LONG_PRESS))
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_GOOD, ButtonMappingStore.defaultActionFor(mode, ClickButton.B, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_UNDO, ButtonMappingStore.defaultActionFor(mode, ClickButton.B, PressType.LONG_PRESS))

        assertEquals(MappedTargetAction.ANKI_FLIP_OR_GOOD, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_UNDO, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_DOWN_LEFT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_UNDO, ButtonMappingStore.defaultActionFor(mode, ClickButton.LEFT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_FLIP_ONLY, ButtonMappingStore.defaultActionFor(mode, ClickButton.RIGHT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_FLIP_ONLY, ButtonMappingStore.defaultActionFor(mode, ClickButton.UP, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_FLIP_ONLY, ButtonMappingStore.defaultActionFor(mode, ClickButton.DOWN, PressType.SINGLE_CLICK))
    }

    @Test
    fun testDefaultActionForMode4MyWhooshOnly() {
        val mode = OperatingMode.MODE_4_MYWHOOSH_ONLY

        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.Z, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.A, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_DOWN_LEFT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_STEER_LEFT, ButtonMappingStore.defaultActionFor(mode, ClickButton.Y, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_STEER_LEFT, ButtonMappingStore.defaultActionFor(mode, ClickButton.LEFT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_STEER_RIGHT, ButtonMappingStore.defaultActionFor(mode, ClickButton.B, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_STEER_RIGHT, ButtonMappingStore.defaultActionFor(mode, ClickButton.RIGHT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_CAMERA, ButtonMappingStore.defaultActionFor(mode, ClickButton.UP, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_EMOTE, ButtonMappingStore.defaultActionFor(mode, ClickButton.DOWN, PressType.SINGLE_CLICK))
    }

    @Test
    fun testDefaultActionForMode5UbuntuAnkiConnect() {
        val mode = OperatingMode.MODE_5_UBUNTU_ANKICONNECT

        // Mode 5 uses the combined ride + Anki split layout
        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.Z, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.A, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_AGAIN, ButtonMappingStore.defaultActionFor(mode, ClickButton.Y, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_UNDO, ButtonMappingStore.defaultActionFor(mode, ClickButton.Y, PressType.LONG_PRESS))
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_GOOD, ButtonMappingStore.defaultActionFor(mode, ClickButton.B, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.ANKI_UNDO, ButtonMappingStore.defaultActionFor(mode, ClickButton.B, PressType.LONG_PRESS))
    }

    @Test
    fun testCanonicalAliasesResolveCorrectly() {
        val mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN

        // Secondary aliases canonicalize to SHIFT_UP_RIGHT
        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_UP_LEFT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_UP_RIGHT_ALT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.POWERUP_RIGHT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_UP, ButtonMappingStore.defaultActionFor(mode, ClickButton.ONOFF_RIGHT, PressType.SINGLE_CLICK))

        // Secondary aliases canonicalize to SHIFT_DOWN_LEFT
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_DOWN_LEFT_ALT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.SHIFT_DOWN_RIGHT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.POWERUP_LEFT, PressType.SINGLE_CLICK))
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, ButtonMappingStore.defaultActionFor(mode, ClickButton.ONOFF_LEFT, PressType.SINGLE_CLICK))
    }

    @Test
    fun testResolveActionWithCustomBindingsOverride() {
        val mode = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN
        val customKey = ModeButtonBindingKey(mode, ClickButton.Z, PressType.SINGLE_CLICK)
        val customBindings = mapOf(customKey to MappedTargetAction.ANKI_FLIP_OR_EASY)

        // Overridden button returns custom target
        val resolved = ButtonMappingStore.resolveAction(mode, ClickButton.Z, PressType.SINGLE_CLICK, customBindings)
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_EASY, resolved)

        // Non-overridden button returns default
        val resolvedDefault = ButtonMappingStore.resolveAction(mode, ClickButton.A, PressType.SINGLE_CLICK, customBindings)
        assertEquals(MappedTargetAction.MW_SHIFT_DOWN, resolvedDefault)

        // Same button in another mode returns that other mode's default
        val resolvedOtherMode = ButtonMappingStore.resolveAction(OperatingMode.MODE_3_ANKI_ONLY, ClickButton.Z, PressType.SINGLE_CLICK, customBindings)
        assertEquals(MappedTargetAction.ANKI_FLIP_OR_HARD, resolvedOtherMode)
    }

    @Test
    fun testSaveAndLoadConfigurationRoundTrip() {
        val tempFile = File.createTempFile("test_settings", ".properties")
        try {
            val customBindings = mapOf(
                ModeButtonBindingKey(
                    OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                    ClickButton.A,
                    PressType.SINGLE_CLICK
                ) to MappedTargetAction.ANKI_FLIP_OR_EASY,
                ModeButtonBindingKey(
                    OperatingMode.MODE_5_UBUNTU_ANKICONNECT,
                    ClickButton.Z,
                    PressType.LONG_PRESS
                ) to MappedTargetAction.ANKI_UNDO
            )

            val originalConfig = AppConfiguration(
                mode = OperatingMode.MODE_5_UBUNTU_ANKICONNECT,
                rightPodOnlyNoZwift = false,
                useZForShiftDownInSinglePodMode = false,
                autoStartOnControllerWake = false,
                floatingHudPillEnabled = true,
                hapticFeedbackEnabled = true,
                audioFeedbackEnabled = true,
                ubuntuAnkiConnectHost = "192.168.1.150",
                ubuntuAnkiConnectPort = 9999,
                leftPodAutoReconnectSec = 30,
                openBikeServerPort = 25000,
                useMyShiftTouchFallbackWhenNoClient = false,
                myShiftUpNormalizedX = 0.88f,
                myShiftUpNormalizedY = 0.77f,
                myShiftDownNormalizedX = 0.66f,
                myShiftDownNormalizedY = 0.55f,
                pinnedRightPodMac = "AA:BB:CC:11:22:33",
                pinnedLeftPodMac = "DD:EE:FF:44:55:66",
                customBindings = customBindings
            )

            ButtonMappingStore.saveConfiguration(originalConfig, tempFile)
            assertTrue(tempFile.exists())

            val loadedConfig = ButtonMappingStore.loadConfiguration(tempFile)

            assertEquals(originalConfig.mode, loadedConfig.mode)
            assertEquals(originalConfig.rightPodOnlyNoZwift, loadedConfig.rightPodOnlyNoZwift)
            assertEquals(originalConfig.useZForShiftDownInSinglePodMode, loadedConfig.useZForShiftDownInSinglePodMode)
            assertEquals(originalConfig.autoStartOnControllerWake, loadedConfig.autoStartOnControllerWake)
            assertEquals(originalConfig.floatingHudPillEnabled, loadedConfig.floatingHudPillEnabled)
            assertEquals(originalConfig.hapticFeedbackEnabled, loadedConfig.hapticFeedbackEnabled)
            assertEquals(originalConfig.audioFeedbackEnabled, loadedConfig.audioFeedbackEnabled)
            assertEquals(originalConfig.ubuntuAnkiConnectHost, loadedConfig.ubuntuAnkiConnectHost)
            assertEquals(originalConfig.ubuntuAnkiConnectPort, loadedConfig.ubuntuAnkiConnectPort)
            assertEquals(originalConfig.leftPodAutoReconnectSec, loadedConfig.leftPodAutoReconnectSec)
            assertEquals(originalConfig.openBikeServerPort, loadedConfig.openBikeServerPort)
            assertEquals(originalConfig.useMyShiftTouchFallbackWhenNoClient, loadedConfig.useMyShiftTouchFallbackWhenNoClient)
            assertEquals(originalConfig.myShiftUpNormalizedX, loadedConfig.myShiftUpNormalizedX, 0.001f)
            assertEquals(originalConfig.myShiftUpNormalizedY, loadedConfig.myShiftUpNormalizedY, 0.001f)
            assertEquals(originalConfig.myShiftDownNormalizedX, loadedConfig.myShiftDownNormalizedX, 0.001f)
            assertEquals(originalConfig.myShiftDownNormalizedY, loadedConfig.myShiftDownNormalizedY, 0.001f)
            assertEquals(originalConfig.pinnedRightPodMac, loadedConfig.pinnedRightPodMac)
            assertEquals(originalConfig.pinnedLeftPodMac, loadedConfig.pinnedLeftPodMac)

            assertEquals(2, loadedConfig.customBindings.size)
            assertEquals(
                MappedTargetAction.ANKI_FLIP_OR_EASY,
                loadedConfig.customBindings[ModeButtonBindingKey(OperatingMode.MODE_1_TABLET_SPLIT_SCREEN, ClickButton.A, PressType.SINGLE_CLICK)]
            )
            assertEquals(
                MappedTargetAction.ANKI_UNDO,
                loadedConfig.customBindings[ModeButtonBindingKey(OperatingMode.MODE_5_UBUNTU_ANKICONNECT, ClickButton.Z, PressType.LONG_PRESS)]
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testLoadConfigurationFromNonExistentFileReturnsDefaults() {
        val nonExistent = File(System.getProperty("java.io.tmpdir"), "non_existent_settings_${System.currentTimeMillis()}.properties")
        val config = ButtonMappingStore.loadConfiguration(nonExistent)
        assertNotNull(config)
        assertEquals(OperatingMode.MODE_1_TABLET_SPLIT_SCREEN, config.mode)
        assertTrue(config.rightPodOnlyNoZwift)
        assertTrue(config.useZForShiftDownInSinglePodMode)
        assertFalse(config.floatingHudPillEnabled)
        assertFalse(config.hapticFeedbackEnabled)
        assertFalse(config.audioFeedbackEnabled)
    }
}

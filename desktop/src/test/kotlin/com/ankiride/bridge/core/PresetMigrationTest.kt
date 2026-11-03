package com.ankiride.bridge.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Properties
import java.util.prefs.Preferences

class PresetMigrationTest {
    @Test fun importsEveryModeAndPreservesOldSettings() {
        val file = kotlin.io.path.createTempFile("legacy-settings", ".properties").toFile()
        val mode1 = OperatingMode.MODE_1_TABLET_SPLIT_SCREEN
        val mode3 = OperatingMode.MODE_3_ANKI_ONLY
        val old = Properties().apply {
            setProperty("operating_mode", mode3.name)
            setProperty("pod_assignment", PodAssignment.LEGACY_SHARED.name)
            setProperty("right_pod_only", "false")
            setProperty("${mode1.name}|Y|SINGLE_CLICK", MappedTargetAction.MW_CAMERA.name)
            setProperty("${mode3.name}|Z|LONG_PRESS", MappedTargetAction.MW_EMOTE.name)
            setProperty("unknown_setting", "keep")
        }
        file.writer().use { old.store(it, null) }
        try {
            val loaded = ButtonMappingStore.loadConfiguration(file)
            assertEquals(OperatingMode.entries.size, loaded.presets.size)
            assertEquals(MappedTargetAction.MW_EMOTE, PresetStore.resolve(loaded, ClickButton.Z, PressType.LONG_PRESS))
            val first = loaded.presets[OperatingMode.entries.indexOf(mode1)]
            assertEquals(MappedTargetAction.MW_CAMERA, first.bindings[PresetBindingKey(ClickButton.Y, PressType.SINGLE_CLICK)])
            assertEquals(first.bindings[PresetBindingKey(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK)], first.bindings[PresetBindingKey(ClickButton.SHIFT_UP_LEFT, PressType.SINGLE_CLICK)])
            ButtonMappingStore.saveConfiguration(loaded, file)
            val reloaded = ButtonMappingStore.loadConfiguration(file)
            assertEquals(loaded.presets, reloaded.presets)
            assertEquals(loaded.activePresetId, reloaded.activePresetId)
            val saved = Properties().apply { file.reader().use { load(it) } }
            assertEquals("keep", saved.getProperty("unknown_setting"))
            assertEquals(MappedTargetAction.MW_CAMERA.name, saved.getProperty("${mode1.name}|Y|SINGLE_CLICK"))
        } finally { file.delete() }
    }
    @Test fun preferencesFallbackRoundTrip() {
        val prefs = Preferences.userRoot().node("/com/ankiride/test-${System.nanoTime()}")
        try {
            val presets = (1..8).map { MappingPreset("id$it", "Preset $it", AnkiTarget.ANKICONNECT, PresetStore.standardBindings()) }
            val config = AppConfiguration(presets = presets, activePresetId = presets.last().id)
            ButtonMappingStore.writeFallbackPresets(prefs, config)
            assertEquals(presets to presets.last().id, ButtonMappingStore.readFallbackPresets(prefs))
        } finally { prefs.removeNode() }
    }
    @Test fun failedFileSaveUsesNewerFallbackUntilFileSaveSucceeds() {
        val prefs = Preferences.userRoot().node("/com/ankiride/test-${System.nanoTime()}")
        val settings = kotlin.io.path.createTempDirectory("settings-failure").toFile()
        val originalProvider = ButtonMappingStore.fallbackPreferences
        try {
            ButtonMappingStore.fallbackPreferences = { prefs }
            val preset = MappingPreset("saved", "Unsaved file", AnkiTarget.ANKICONNECT, PresetStore.standardBindings())
            val config = AppConfiguration(presets = listOf(preset), activePresetId = preset.id, ubuntuAnkiConnectHost = "10.1.2.3")
            ButtonMappingStore.saveConfiguration(config, settings) // A directory cannot be read as a properties file.
            val fallback = ButtonMappingStore.loadConfiguration(settings)
            assertEquals(config.presets, fallback.presets)
            assertEquals(config.ubuntuAnkiConnectHost, fallback.ubuntuAnkiConnectHost)
            assertTrue(settings.delete())
            val recovered = config.copy(ubuntuAnkiConnectHost = "10.2.3.4")
            ButtonMappingStore.saveConfiguration(recovered, settings)
            assertEquals("10.2.3.4", ButtonMappingStore.loadConfiguration(settings).ubuntuAnkiConnectHost)
        } finally {
            ButtonMappingStore.fallbackPreferences = originalProvider
            settings.delete()
            prefs.removeNode()
        }
    }

}

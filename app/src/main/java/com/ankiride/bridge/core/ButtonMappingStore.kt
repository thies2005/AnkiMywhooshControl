package com.ankiride.bridge.core

import android.content.Context

object ButtonMappingStore {
    const val KEY_USE_Z_SHIFT_DOWN = "use_z_for_shift_down"
    private const val PREFS_BINDINGS = "ankiride_per_mode_bindings_v4"
    private const val PREFS_SETTINGS = "ankiride_app_settings_v4"

    private const val KEY_ASSIGNMENT = "pod_assignment"
    private const val KEY_RIGHT_PIN = "pinned_right_pod_mac"
    private const val KEY_LEFT_PIN = "pinned_left_pod_mac"
    private const val KEY_MODE = "operating_mode"
    private const val KEY_RIGHT_POD_ONLY = "right_pod_only"
    private const val KEY_AUTO_START = "auto_start_on_wake"
    private const val KEY_FLOATING_HUD = "floating_hud_pill_enabled"
    private const val KEY_HAPTIC = "haptic_feedback_enabled"
    private const val KEY_AUDIO = "audio_feedback_enabled"
    private const val KEY_UBUNTU_HOST = "ubuntu_ankiconnect_host"
    private const val KEY_UBUNTU_PORT = "ubuntu_ankiconnect_port"
    private const val KEY_TOUCH_FALLBACK = "touch_fallback"

    fun resolveAction(
        mode: OperatingMode,
        button: ClickButton,
        pressType: PressType,
        customBindings: Map<ModeButtonBindingKey, MappedTargetAction> = emptyMap(),
        rightPodOnlyNoZwift: Boolean = false,
        useZForShiftDownInSinglePodMode: Boolean = false,
        podAssignment: PodAssignment = PodAssignment.LEGACY_SHARED
    ): MappedTargetAction {
        if (podAssignment != PodAssignment.LEGACY_SHARED) {
            val canonical = button.canonicalButton
            if (!canonical.isRightPod) {
                if (podAssignment == PodAssignment.RIGHT_ANKI_ONLY) return MappedTargetAction.NONE
                return when (canonical) {
                    ClickButton.SHIFT_UP_LEFT -> MappedTargetAction.MW_SHIFT_UP
                    ClickButton.SHIFT_DOWN_LEFT -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.MW_SHIFT_UP else MappedTargetAction.MW_SHIFT_DOWN
                    ClickButton.LEFT -> MappedTargetAction.MW_STEER_LEFT
                    ClickButton.RIGHT -> MappedTargetAction.MW_STEER_RIGHT
                    ClickButton.UP -> MappedTargetAction.MW_CAMERA
                    ClickButton.DOWN -> MappedTargetAction.MW_EMOTE
                    else -> MappedTargetAction.NONE
                }
            }
            return when (canonical) {
                ClickButton.Y, ClickButton.SHIFT_DOWN_RIGHT -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_OR_AGAIN
                ClickButton.Z -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_OR_HARD
                ClickButton.B -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_OR_GOOD
                ClickButton.A -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_FLIP_OR_EASY else MappedTargetAction.ANKI_FLIP_OR_GOOD
                else -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_ONLY
            }
        }
        val canonical = legacyCanonical(button)
        val key = ModeButtonBindingKey(mode, canonical, pressType)
        customBindings[key]?.let { return it }

        if (rightPodOnlyNoZwift && useZForShiftDownInSinglePodMode) {
            if (canonical == ClickButton.Z && pressType == PressType.SINGLE_CLICK) {
                when (mode) {
                    OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                    OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
                    OperatingMode.MODE_4_MYWHOOSH_ONLY,
                    OperatingMode.MODE_5_UBUNTU_ANKICONNECT -> {
                        return MappedTargetAction.MW_SHIFT_DOWN
                    }
                    else -> {}
                }
            }
        }

        return defaultActionFor(mode, canonical, pressType, rightPodOnlyNoZwift, useZForShiftDownInSinglePodMode)
    }

    fun defaultActionFor(
        mode: OperatingMode,
        button: ClickButton,
        pressType: PressType,
        rightPodOnlyNoZwift: Boolean = false,
        useZForShiftDownInSinglePodMode: Boolean = false
    ): MappedTargetAction {
        val canonical = legacyCanonical(button)
        if (rightPodOnlyNoZwift && useZForShiftDownInSinglePodMode) {
            if (canonical == ClickButton.Z && pressType == PressType.SINGLE_CLICK) {
                when (mode) {
                    OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
                    OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
                    OperatingMode.MODE_4_MYWHOOSH_ONLY,
                    OperatingMode.MODE_5_UBUNTU_ANKICONNECT -> {
                        return MappedTargetAction.MW_SHIFT_DOWN
                    }
                    else -> {}
                }
            }
        }
        return when (mode) {
            OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
            OperatingMode.MODE_5_UBUNTU_ANKICONNECT -> {
                // Default 4-Colorful-Button Split for all Combined Ride + Anki Modes (Modes 1, 2, and 5):
                // Z (Left Orange) = Shift Up (+), A (Right Green) = Shift Down (–)
                // Y (Top Cyan) = Anki Didn't Know (Again), B (Bottom Pink) = Anki Good
                // Bottom '+' Circle (Below B) = Shift Up (+), Bottom '–' Circle (Below v) = Shift Down (–)
                when (canonical) {
                    ClickButton.Z -> MappedTargetAction.MW_SHIFT_UP
                    ClickButton.A -> MappedTargetAction.MW_SHIFT_DOWN
                    ClickButton.Y -> if (pressType == PressType.LONG_PRESS) {
                        MappedTargetAction.ANKI_UNDO
                    } else {
                        MappedTargetAction.ANKI_FLIP_OR_AGAIN
                    }
                    ClickButton.B -> if (pressType == PressType.LONG_PRESS) {
                        MappedTargetAction.ANKI_UNDO
                    } else {
                        MappedTargetAction.ANKI_FLIP_OR_GOOD
                    }
                    ClickButton.SHIFT_UP_RIGHT -> if (pressType == PressType.LONG_PRESS) {
                        MappedTargetAction.MW_SHIFT_DOWN
                    } else {
                        MappedTargetAction.MW_SHIFT_UP
                    }
                    ClickButton.SHIFT_DOWN_LEFT -> MappedTargetAction.MW_SHIFT_DOWN
                    ClickButton.LEFT -> MappedTargetAction.MW_STEER_LEFT
                    ClickButton.RIGHT -> MappedTargetAction.MW_STEER_RIGHT
                    ClickButton.UP -> MappedTargetAction.MW_CAMERA
                    ClickButton.DOWN -> MappedTargetAction.MW_EMOTE
                    else -> MappedTargetAction.NONE
                }
            }

            OperatingMode.MODE_3_ANKI_ONLY -> {
                when (canonical) {
                    ClickButton.Y -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_OR_AGAIN
                    ClickButton.B -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_OR_GOOD
                    ClickButton.A -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_FLIP_OR_EASY else MappedTargetAction.ANKI_FLIP_OR_GOOD
                    ClickButton.Z -> if (pressType == PressType.LONG_PRESS) MappedTargetAction.ANKI_UNDO else MappedTargetAction.ANKI_FLIP_OR_HARD
                    ClickButton.SHIFT_UP_RIGHT -> MappedTargetAction.ANKI_FLIP_OR_GOOD
                    ClickButton.SHIFT_DOWN_LEFT, ClickButton.LEFT -> MappedTargetAction.ANKI_UNDO
                    ClickButton.RIGHT, ClickButton.UP, ClickButton.DOWN -> MappedTargetAction.ANKI_FLIP_ONLY
                    else -> MappedTargetAction.NONE
                }
            }

            OperatingMode.MODE_4_MYWHOOSH_ONLY -> {
                when (canonical) {
                    ClickButton.Z, ClickButton.SHIFT_UP_RIGHT -> MappedTargetAction.MW_SHIFT_UP
                    ClickButton.A, ClickButton.SHIFT_DOWN_LEFT -> MappedTargetAction.MW_SHIFT_DOWN
                    ClickButton.Y, ClickButton.LEFT -> MappedTargetAction.MW_STEER_LEFT
                    ClickButton.B, ClickButton.RIGHT -> MappedTargetAction.MW_STEER_RIGHT
                    ClickButton.UP -> MappedTargetAction.MW_CAMERA
                    ClickButton.DOWN -> MappedTargetAction.MW_EMOTE
                    else -> MappedTargetAction.NONE
                }
            }
        }
    }

    private fun legacyCanonical(button: ClickButton): ClickButton = when (button) {
        ClickButton.SHIFT_UP_LEFT -> ClickButton.SHIFT_UP_RIGHT
        ClickButton.SHIFT_DOWN_RIGHT -> ClickButton.SHIFT_DOWN_LEFT
        else -> button.canonicalButton
    }

    fun loadBindings(context: Context): Map<ModeButtonBindingKey, MappedTargetAction> {
        val prefs = context.getSharedPreferences(PREFS_BINDINGS, Context.MODE_PRIVATE)
        val result = mutableMapOf<ModeButtonBindingKey, MappedTargetAction>()
        for ((rawKey, rawVal) in prefs.all) {
            val parts = rawKey.split("|")
            if (parts.size != 3) continue
            val mode = OperatingMode.entries.firstOrNull { it.name == parts[0] } ?: continue
            val button = ClickButton.entries.firstOrNull { it.name == parts[1] } ?: continue
            val press = PressType.entries.firstOrNull { it.name == parts[2] } ?: continue
            val target = MappedTargetAction.entries.firstOrNull { it.name == (rawVal as? String) } ?: continue
            result[ModeButtonBindingKey(mode, button, press)] = target
        }
        return result
    }

    fun saveBindings(context: Context, bindings: Map<ModeButtonBindingKey, MappedTargetAction>) {
        val editor = context.getSharedPreferences(PREFS_BINDINGS, Context.MODE_PRIVATE).edit()
        editor.clear()
        for ((k, v) in bindings) {
            editor.putString("${k.mode.name}|${k.button.name}|${k.pressType.name}", v.name)
        }
        editor.apply()
    }

    internal fun podSettingsFrom(settings: Map<String, *>): Triple<PodAssignment, String?, String?> {
        val assignment = (settings[KEY_ASSIGNMENT] as? String)?.let { name -> PodAssignment.entries.firstOrNull { it.name == name } }
            ?: if (settings.isEmpty()) PodAssignment.RIGHT_ANKI_ONLY else PodAssignment.LEGACY_SHARED
        return Triple(assignment, settings[KEY_RIGHT_PIN] as? String, settings[KEY_LEFT_PIN] as? String)
    }

    internal fun podSettingsFor(config: AppConfiguration): Map<String, String?> = mapOf(
        KEY_ASSIGNMENT to config.podAssignment.name,
        KEY_RIGHT_PIN to config.pinnedRightPodMac?.trim()?.ifBlank { null },
        KEY_LEFT_PIN to config.pinnedLeftPodMac?.trim()?.ifBlank { null }
    )

    fun loadFullConfiguration(context: Context): AppConfiguration {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val defaultMode = if (prefs.all.isEmpty()) OperatingMode.MODE_5_UBUNTU_ANKICONNECT else OperatingMode.MODE_1_TABLET_SPLIT_SCREEN
        val modeName = prefs.getString(KEY_MODE, defaultMode.name)
        val mode = OperatingMode.entries.firstOrNull { it.name == modeName }
            ?: OperatingMode.MODE_1_TABLET_SPLIT_SCREEN
        val config = AppConfiguration(
            mode = mode,
            podAssignment = podSettingsFrom(prefs.all).first,
            pinnedRightPodMac = podSettingsFrom(prefs.all).second,
            pinnedLeftPodMac = podSettingsFrom(prefs.all).third,
            rightPodOnlyNoZwift = prefs.getBoolean(KEY_RIGHT_POD_ONLY, true),
            useZForShiftDownInSinglePodMode = prefs.getBoolean(KEY_USE_Z_SHIFT_DOWN, true),
            autoStartOnControllerWake = prefs.getBoolean(KEY_AUTO_START, true),
            floatingHudPillEnabled = prefs.getBoolean(KEY_FLOATING_HUD, false), // Default OFF
            hapticFeedbackEnabled = prefs.getBoolean(KEY_HAPTIC, false),        // Default OFF
            audioFeedbackEnabled = prefs.getBoolean(KEY_AUDIO, false),          // Default OFF
            ubuntuAnkiConnectHost = prefs.getString(KEY_UBUNTU_HOST, "192.168.1.100") ?: "192.168.1.100",
            ubuntuAnkiConnectPort = prefs.getInt(KEY_UBUNTU_PORT, 8765),
            useMyShiftTouchFallbackWhenNoClient = prefs.getBoolean(KEY_TOUCH_FALLBACK, true),
            customBindings = loadBindings(context)
        )
        val saved = PresetStore.deserialize(prefs.getString("named_presets_v1", null))
        if (saved != null) return config.copy(presets = saved.first, activePresetId = saved.second ?: saved.first.first().id)
        val existing = prefs.all.isNotEmpty() || config.customBindings.isNotEmpty()
        val modes = if (existing) OperatingMode.entries else listOf(mode)
        val presets = modes.map { oldMode ->
            PresetStore.snapshot(if (existing) "Imported · ${oldMode.shortTitle}" else "My controls", if (oldMode == OperatingMode.MODE_5_UBUNTU_ANKICONNECT) AnkiTarget.ANKICONNECT else AnkiTarget.ANKIDROID) { button, press ->
                resolveAction(oldMode, button, press, config.customBindings, config.rightPodOnlyNoZwift, config.useZForShiftDownInSinglePodMode, config.podAssignment)
            }
        }
        return config.copy(presets = presets, activePresetId = presets[modes.indexOf(mode).coerceAtLeast(0)].id)
    }

    fun saveFullConfiguration(context: Context, config: AppConfiguration) {
        val editor = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit()
        if (config.presets.isNotEmpty()) editor.putString("named_presets_v1", PresetStore.serialize(config.presets, config.activePresetId))
        podSettingsFor(config).forEach { (key, value) -> editor.putString(key, value) }
        editor.putString(KEY_MODE, config.mode.name)
            .putBoolean(KEY_RIGHT_POD_ONLY, config.rightPodOnlyNoZwift)
            .putBoolean(KEY_USE_Z_SHIFT_DOWN, config.useZForShiftDownInSinglePodMode)
            .putBoolean(KEY_AUTO_START, config.autoStartOnControllerWake)
            .putBoolean(KEY_FLOATING_HUD, config.floatingHudPillEnabled)
            .putBoolean(KEY_HAPTIC, config.hapticFeedbackEnabled)
            .putBoolean(KEY_AUDIO, config.audioFeedbackEnabled)
            .putString(KEY_UBUNTU_HOST, config.ubuntuAnkiConnectHost.trim())
            .putInt(KEY_UBUNTU_PORT, config.ubuntuAnkiConnectPort)
            .putBoolean(KEY_TOUCH_FALLBACK, config.useMyShiftTouchFallbackWhenNoClient)
            .apply()
        saveBindings(context, config.customBindings)
    }
}

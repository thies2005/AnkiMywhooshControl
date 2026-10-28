package com.ankiride.bridge.core

import android.content.Context

object ButtonMappingStore {
    const val KEY_USE_Z_SHIFT_DOWN = "use_z_for_shift_down"
    private const val PREFS_BINDINGS = "ankiride_per_mode_bindings_v4"
    private const val PREFS_SETTINGS = "ankiride_app_settings_v4"

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
        useZForShiftDownInSinglePodMode: Boolean = false
    ): MappedTargetAction {
        val canonical = button.canonicalButton
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
        val canonical = button.canonicalButton
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

    fun loadFullConfiguration(context: Context): AppConfiguration {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val modeName = prefs.getString(KEY_MODE, OperatingMode.MODE_1_TABLET_SPLIT_SCREEN.name)
        val mode = OperatingMode.entries.firstOrNull { it.name == modeName }
            ?: OperatingMode.MODE_1_TABLET_SPLIT_SCREEN
        return AppConfiguration(
            mode = mode,
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
    }

    fun saveFullConfiguration(context: Context, config: AppConfiguration) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, config.mode.name)
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

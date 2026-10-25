package com.ankiride.bridge.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.prefs.Preferences

object ButtonMappingStore {

    private const val KEY_MODE = "operating_mode"
    private const val KEY_RIGHT_POD_ONLY = "right_pod_only"
    const val KEY_USE_Z_SHIFT_DOWN = "use_z_for_shift_down"
    private const val KEY_USE_Z_FOR_SHIFT_DOWN = KEY_USE_Z_SHIFT_DOWN
    private const val KEY_AUTO_START = "auto_start_on_wake"
    private const val KEY_FLOATING_HUD = "floating_hud_pill_enabled"
    private const val KEY_HAPTIC = "haptic_feedback_enabled"
    private const val KEY_AUDIO = "audio_feedback_enabled"
    private const val KEY_UBUNTU_HOST = "ubuntu_ankiconnect_host"
    private const val KEY_UBUNTU_PORT = "ubuntu_ankiconnect_port"
    private const val KEY_LEFT_POD_RECONNECT = "left_pod_auto_reconnect_sec"
    private const val KEY_OPENBIKE_PORT = "open_bike_server_port"
    private const val KEY_TOUCH_FALLBACK = "touch_fallback"
    private const val KEY_SHIFT_UP_X = "my_shift_up_norm_x"
    private const val KEY_SHIFT_UP_Y = "my_shift_up_norm_y"
    private const val KEY_SHIFT_DOWN_X = "my_shift_down_norm_x"
    private const val KEY_SHIFT_DOWN_Y = "my_shift_down_norm_y"
    private const val KEY_PINNED_RIGHT_MAC = "pinned_right_pod_mac"
    private const val KEY_PINNED_LEFT_MAC = "pinned_left_pod_mac"

    fun defaultSettingsFile(): File {
        val dir = File(System.getProperty("user.home"), ".ankiride")
        return File(dir, "settings.properties")
    }

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
                // Z (Top Orange) = Shift Up (+), A (Bottom Blue) = Shift Down (–)
                // Y (Left Yellow) = Anki Didn't Know (Again), B (Right Green) = Anki Good
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

    fun loadConfiguration(file: File = defaultSettingsFile()): AppConfiguration {
        val props = Properties()
        var loadedFromFile = false

        if (file.exists() && file.isFile) {
            try {
                FileInputStream(file).use { fis ->
                    InputStreamReader(fis, StandardCharsets.UTF_8).use { reader ->
                        props.load(reader)
                    }
                }
                loadedFromFile = true
            } catch (e: Exception) {
                DiagnosticLogger.warn(LogCategory.SYSTEM, "Failed to read ${file.absolutePath}: ${e.message}")
            }
        }

        if (!loadedFromFile) {
            val fallback = loadFromPreferencesFallback()
            if (fallback != null) {
                return fallback
            }
        }

        val modeName = props.getProperty(KEY_MODE, OperatingMode.MODE_1_TABLET_SPLIT_SCREEN.name)
        val mode = OperatingMode.entries.firstOrNull { it.name == modeName } ?: OperatingMode.MODE_1_TABLET_SPLIT_SCREEN

        val customBindings = mutableMapOf<ModeButtonBindingKey, MappedTargetAction>()
        for (name in props.stringPropertyNames()) {
            val parts = name.split("|")
            if (parts.size == 3) {
                val m = OperatingMode.entries.firstOrNull { it.name == parts[0] } ?: continue
                val b = ClickButton.entries.firstOrNull { it.name == parts[1] } ?: continue
                val p = PressType.entries.firstOrNull { it.name == parts[2] } ?: continue
                val actionName = props.getProperty(name)
                val action = MappedTargetAction.entries.firstOrNull { it.name == actionName } ?: continue
                customBindings[ModeButtonBindingKey(m, b, p)] = action
            }
        }

        return AppConfiguration(
            mode = mode,
            rightPodOnlyNoZwift = props.getProperty(KEY_RIGHT_POD_ONLY, "true").toBoolean(),
            useZForShiftDownInSinglePodMode = props.getProperty(KEY_USE_Z_FOR_SHIFT_DOWN, "true").toBoolean(),
            autoStartOnControllerWake = props.getProperty(KEY_AUTO_START, "true").toBoolean(),
            floatingHudPillEnabled = props.getProperty(KEY_FLOATING_HUD, "false").toBoolean(),
            hapticFeedbackEnabled = props.getProperty(KEY_HAPTIC, "false").toBoolean(),
            audioFeedbackEnabled = props.getProperty(KEY_AUDIO, "false").toBoolean(),
            ubuntuAnkiConnectHost = props.getProperty(KEY_UBUNTU_HOST, "192.168.1.100").trim(),
            ubuntuAnkiConnectPort = props.getProperty(KEY_UBUNTU_PORT, "8765").toIntOrNull() ?: 8765,
            leftPodAutoReconnectSec = props.getProperty(KEY_LEFT_POD_RECONNECT, "55").toIntOrNull() ?: 55,
            openBikeServerPort = props.getProperty(KEY_OPENBIKE_PORT, "21587").toIntOrNull() ?: 21587,
            useMyShiftTouchFallbackWhenNoClient = props.getProperty(KEY_TOUCH_FALLBACK, "true").toBoolean(),
            myShiftUpNormalizedX = props.getProperty(KEY_SHIFT_UP_X, "0.92").toFloatOrNull() ?: 0.92f,
            myShiftUpNormalizedY = props.getProperty(KEY_SHIFT_UP_Y, "0.85").toFloatOrNull() ?: 0.85f,
            myShiftDownNormalizedX = props.getProperty(KEY_SHIFT_DOWN_X, "0.84").toFloatOrNull() ?: 0.84f,
            myShiftDownNormalizedY = props.getProperty(KEY_SHIFT_DOWN_Y, "0.85").toFloatOrNull() ?: 0.85f,
            pinnedRightPodMac = props.getProperty(KEY_PINNED_RIGHT_MAC)?.takeIf { it.isNotBlank() },
            pinnedLeftPodMac = props.getProperty(KEY_PINNED_LEFT_MAC)?.takeIf { it.isNotBlank() },
            customBindings = customBindings
        )
    }

    fun saveConfiguration(config: AppConfiguration, file: File = defaultSettingsFile()) {
        val props = Properties()
        props.setProperty(KEY_MODE, config.mode.name)
        props.setProperty(KEY_RIGHT_POD_ONLY, config.rightPodOnlyNoZwift.toString())
        props.setProperty(KEY_USE_Z_FOR_SHIFT_DOWN, config.useZForShiftDownInSinglePodMode.toString())
        props.setProperty(KEY_AUTO_START, config.autoStartOnControllerWake.toString())
        props.setProperty(KEY_FLOATING_HUD, config.floatingHudPillEnabled.toString())
        props.setProperty(KEY_HAPTIC, config.hapticFeedbackEnabled.toString())
        props.setProperty(KEY_AUDIO, config.audioFeedbackEnabled.toString())
        props.setProperty(KEY_UBUNTU_HOST, config.ubuntuAnkiConnectHost.trim())
        props.setProperty(KEY_UBUNTU_PORT, config.ubuntuAnkiConnectPort.toString())
        props.setProperty(KEY_LEFT_POD_RECONNECT, config.leftPodAutoReconnectSec.toString())
        props.setProperty(KEY_OPENBIKE_PORT, config.openBikeServerPort.toString())
        props.setProperty(KEY_TOUCH_FALLBACK, config.useMyShiftTouchFallbackWhenNoClient.toString())
        props.setProperty(KEY_SHIFT_UP_X, config.myShiftUpNormalizedX.toString())
        props.setProperty(KEY_SHIFT_UP_Y, config.myShiftUpNormalizedY.toString())
        props.setProperty(KEY_SHIFT_DOWN_X, config.myShiftDownNormalizedX.toString())
        props.setProperty(KEY_SHIFT_DOWN_Y, config.myShiftDownNormalizedY.toString())
        config.pinnedRightPodMac?.let { props.setProperty(KEY_PINNED_RIGHT_MAC, it) }
        config.pinnedLeftPodMac?.let { props.setProperty(KEY_PINNED_LEFT_MAC, it) }

        for ((key, action) in config.customBindings) {
            val serializedKey = "${key.mode.name}|${key.button.name}|${key.pressType.name}"
            props.setProperty(serializedKey, action.name)
        }

        try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    props.store(writer, "AnkiRideBridge Desktop Settings")
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.SYSTEM, "Failed to save settings to ${file.absolutePath}: ${e.message}")
            saveToPreferencesFallback(config)
        }
    }

    fun loadFullConfiguration(): AppConfiguration = loadConfiguration()
    fun saveFullConfiguration(config: AppConfiguration) = saveConfiguration(config)

    private fun loadFromPreferencesFallback(): AppConfiguration? {
        return try {
            val prefs = Preferences.userNodeForPackage(ButtonMappingStore::class.java)
            val modeName = prefs.get(KEY_MODE, null) ?: return null
            val mode = OperatingMode.entries.firstOrNull { it.name == modeName } ?: OperatingMode.MODE_1_TABLET_SPLIT_SCREEN
            AppConfiguration(
                mode = mode,
                rightPodOnlyNoZwift = prefs.getBoolean(KEY_RIGHT_POD_ONLY, true),
                useZForShiftDownInSinglePodMode = prefs.getBoolean(KEY_USE_Z_FOR_SHIFT_DOWN, true),
                autoStartOnControllerWake = prefs.getBoolean(KEY_AUTO_START, true),
                floatingHudPillEnabled = prefs.getBoolean(KEY_FLOATING_HUD, false),
                hapticFeedbackEnabled = prefs.getBoolean(KEY_HAPTIC, false),
                audioFeedbackEnabled = prefs.getBoolean(KEY_AUDIO, false),
                ubuntuAnkiConnectHost = prefs.get(KEY_UBUNTU_HOST, "192.168.1.100"),
                ubuntuAnkiConnectPort = prefs.getInt(KEY_UBUNTU_PORT, 8765),
                leftPodAutoReconnectSec = prefs.getInt(KEY_LEFT_POD_RECONNECT, 55),
                openBikeServerPort = prefs.getInt(KEY_OPENBIKE_PORT, 21587),
                useMyShiftTouchFallbackWhenNoClient = prefs.getBoolean(KEY_TOUCH_FALLBACK, true),
                myShiftUpNormalizedX = prefs.getFloat(KEY_SHIFT_UP_X, 0.92f),
                myShiftUpNormalizedY = prefs.getFloat(KEY_SHIFT_UP_Y, 0.85f),
                myShiftDownNormalizedX = prefs.getFloat(KEY_SHIFT_DOWN_X, 0.84f),
                myShiftDownNormalizedY = prefs.getFloat(KEY_SHIFT_DOWN_Y, 0.85f),
                pinnedRightPodMac = prefs.get(KEY_PINNED_RIGHT_MAC, null),
                pinnedLeftPodMac = prefs.get(KEY_PINNED_LEFT_MAC, null)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun saveToPreferencesFallback(config: AppConfiguration) {
        try {
            val prefs = Preferences.userNodeForPackage(ButtonMappingStore::class.java)
            prefs.put(KEY_MODE, config.mode.name)
            prefs.putBoolean(KEY_RIGHT_POD_ONLY, config.rightPodOnlyNoZwift)
            prefs.putBoolean(KEY_USE_Z_FOR_SHIFT_DOWN, config.useZForShiftDownInSinglePodMode)
            prefs.putBoolean(KEY_AUTO_START, config.autoStartOnControllerWake)
            prefs.putBoolean(KEY_FLOATING_HUD, config.floatingHudPillEnabled)
            prefs.putBoolean(KEY_HAPTIC, config.hapticFeedbackEnabled)
            prefs.putBoolean(KEY_AUDIO, config.audioFeedbackEnabled)
            prefs.put(KEY_UBUNTU_HOST, config.ubuntuAnkiConnectHost.trim())
            prefs.putInt(KEY_UBUNTU_PORT, config.ubuntuAnkiConnectPort)
            prefs.putInt(KEY_LEFT_POD_RECONNECT, config.leftPodAutoReconnectSec)
            prefs.putInt(KEY_OPENBIKE_PORT, config.openBikeServerPort)
            prefs.putBoolean(KEY_TOUCH_FALLBACK, config.useMyShiftTouchFallbackWhenNoClient)
            prefs.putFloat(KEY_SHIFT_UP_X, config.myShiftUpNormalizedX)
            prefs.putFloat(KEY_SHIFT_UP_Y, config.myShiftUpNormalizedY)
            prefs.putFloat(KEY_SHIFT_DOWN_X, config.myShiftDownNormalizedX)
            prefs.putFloat(KEY_SHIFT_DOWN_Y, config.myShiftDownNormalizedY)
            if (config.pinnedRightPodMac != null) prefs.put(KEY_PINNED_RIGHT_MAC, config.pinnedRightPodMac)
            if (config.pinnedLeftPodMac != null) prefs.put(KEY_PINNED_LEFT_MAC, config.pinnedLeftPodMac)
            prefs.flush()
        } catch (_: Exception) {
        }
    }
}

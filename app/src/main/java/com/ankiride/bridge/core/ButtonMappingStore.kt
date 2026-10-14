package com.ankiride.bridge.core

import android.content.Context

object ButtonMappingStore {
    private const val PREFS_NAME = "ankiride_per_mode_bindings"

    /**
     * Resolves the active MappedTargetAction for a given (mode, button, pressType),
     * checking per-mode user overrides first, then falling back to the mode's default preset.
     */
    fun resolveAction(
        mode: OperatingMode,
        button: ClickButton,
        pressType: PressType,
        customBindings: Map<ModeButtonBindingKey, MappedTargetAction>
    ): MappedTargetAction {
        val canonical = button.canonicalButton
        val key = ModeButtonBindingKey(mode, canonical, pressType)
        customBindings[key]?.let { return it }
        return defaultActionFor(mode, canonical, pressType)
    }

    fun defaultActionFor(
        mode: OperatingMode,
        button: ClickButton,
        pressType: PressType
    ): MappedTargetAction {
        val canonical = button.canonicalButton
        return when (mode) {
            OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH -> {
                // Default 4-Colorful-Button Split:
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

    fun loadBindings(context: Context): Map<ModeButtonBindingKey, MappedTargetAction> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.clear()
        for ((k, v) in bindings) {
            editor.putString("${k.mode.name}|${k.button.name}|${k.pressType.name}", v.name)
        }
        editor.apply()
    }
}

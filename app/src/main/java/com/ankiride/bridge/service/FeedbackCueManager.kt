package com.ankiride.bridge.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.MappedTargetAction
import com.ankiride.bridge.core.MyWhooshAction

object FeedbackCueManager {
    private var cachedToneGenerator: ToneGenerator? = null

    @Synchronized
    private fun getToneGenerator(): ToneGenerator? {
        if (cachedToneGenerator == null) {
            try {
                cachedToneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)
            } catch (_: Exception) {
            }
        }
        return cachedToneGenerator
    }

    @Synchronized
    fun release() {
        try {
            cachedToneGenerator?.release()
        } catch (_: Exception) {
        } finally {
            cachedToneGenerator = null
        }
    }

    fun triggerCue(context: Context, target: MappedTargetAction, config: AppConfiguration) {
        if (target == MappedTargetAction.NONE) return
        if (config.hapticFeedbackEnabled) {
            triggerHaptic(context, target)
        }
        if (config.audioFeedbackEnabled) {
            triggerAudioTone(target)
        }
    }

    private fun triggerHaptic(context: Context, target: MappedTargetAction) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when {
                    target.myWhooshAction == MyWhooshAction.SHIFT_UP ->
                        VibrationEffect.createOneShot(20L, 220)
                    target.myWhooshAction == MyWhooshAction.SHIFT_DOWN ->
                        VibrationEffect.createWaveform(longArrayOf(0L, 15L, 45L, 15L), intArrayOf(0, 200, 0, 200), -1)
                    target.ankiAction == AnkiAction.UNDO ->
                        VibrationEffect.createWaveform(longArrayOf(0L, 45L, 60L, 65L), intArrayOf(0, 255, 0, 255), -1)
                    target.ankiAction == AnkiAction.FLIP_OR_AGAIN ->
                        VibrationEffect.createOneShot(45L, 180)
                    target.ankiAction != null ->
                        VibrationEffect.createOneShot(25L, 130)
                    else ->
                        VibrationEffect.createOneShot(18L, 120)
                }
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun triggerAudioTone(target: MappedTargetAction) {
        try {
            val toneType = when {
                target.myWhooshAction == MyWhooshAction.SHIFT_UP -> ToneGenerator.TONE_PROP_BEEP
                target.myWhooshAction == MyWhooshAction.SHIFT_DOWN -> ToneGenerator.TONE_PROP_ACK
                target.ankiAction == AnkiAction.UNDO -> ToneGenerator.TONE_PROP_NACK
                target.ankiAction == AnkiAction.FLIP_OR_AGAIN -> ToneGenerator.TONE_CDMA_PIP
                target.ankiAction != null -> ToneGenerator.TONE_PROP_BEEP2
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            getToneGenerator()?.startTone(toneType, 45)
        } catch (_: Exception) {
        }
    }
}

package com.ankiride.bridge.service

import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.MappedTargetAction
import com.ankiride.bridge.core.MyWhooshAction
import java.awt.Toolkit
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

object DesktopFeedbackCueManager {

    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DesktopFeedbackToneThread").apply { isDaemon = true }
    }

    fun triggerCue(target: MappedTargetAction, config: AppConfiguration) {
        if (target == MappedTargetAction.NONE || !config.audioFeedbackEnabled) return
        audioExecutor.execute {
            playToneForAction(target)
        }
    }

    private fun playToneForAction(target: MappedTargetAction) {
        val (freq, durationMs) = when {
            target.myWhooshAction == MyWhooshAction.SHIFT_UP -> 880.0 to 35
            target.myWhooshAction == MyWhooshAction.SHIFT_DOWN -> 440.0 to 35
            target.ankiAction == AnkiAction.UNDO -> 300.0 to 60
            target.ankiAction == AnkiAction.FLIP_OR_AGAIN -> 350.0 to 45
            target.ankiAction == AnkiAction.FLIP_OR_GOOD -> 700.0 to 40
            target.ankiAction != null -> 600.0 to 35
            else -> 520.0 to 30
        }

        try {
            val sampleRate = 44100f
            val numSamples = (durationMs * sampleRate / 1000).toInt()
            val buffer = ByteArray(numSamples)
            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                // Linear decay envelope to prevent popping/clicking artifacts
                val envelope = 1.0 - (i.toDouble() / numSamples)
                buffer[i] = (Math.sin(angle) * 50.0 * envelope).toInt().toByte()
            }

            val format = AudioFormat(sampleRate, 8, 1, true, false)
            val line = AudioSystem.getSourceDataLine(format)
            line.open(format)
            line.start()
            line.write(buffer, 0, buffer.size)
            line.drain()
            line.close()
        } catch (_: Throwable) {
            try {
                Toolkit.getDefaultToolkit().beep()
            } catch (_: Throwable) {
            }
        }
    }
}

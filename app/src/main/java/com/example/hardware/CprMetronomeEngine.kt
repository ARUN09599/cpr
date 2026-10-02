package com.example.hardware

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AHA 100–120 BPM Audio + Haptic Metronome Engine for CPR Compression Pacing.
 */
class CprMetronomeEngine(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    } catch (_: Exception) {
        null
    }

    private var metronomeJob: Job? = null
    private var beatCounter = 0

    fun start(
        scope: CoroutineScope,
        bpmProvider: () -> Int,
        hapticEnabledProvider: () -> Boolean,
        onBeat: (Int) -> Unit
    ) {
        stop()
        beatCounter = 0
        metronomeJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val bpm = bpmProvider().coerceIn(90, 140)
                val intervalMs = (60000L / bpm).coerceAtLeast(350L)
                beatCounter = (beatCounter % 30) + 1
                onBeat(beatCounter)

                // Accent every 10th compression and on 30th compression
                val toneType = when (beatCounter) {
                    30 -> ToneGenerator.TONE_PROP_ACK
                    10, 20 -> ToneGenerator.TONE_PROP_BEEP2
                    else -> ToneGenerator.TONE_PROP_BEEP
                }
                try {
                    toneGenerator?.startTone(toneType, 45)
                } catch (_: Exception) {
                }

                if (hapticEnabledProvider()) {
                    triggerPulseVibration(isAccent = (beatCounter == 30))
                }

                delay(intervalMs)
            }
        }
    }

    fun stop() {
        metronomeJob?.cancel()
        metronomeJob = null
    }

    private fun triggerPulseVibration(isAccent: Boolean) {
        try {
            val duration = if (isAccent) 65L else 28L
            val amplitude = if (isAccent) 255 else 150
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        stop()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {
        }
        toneGenerator = null
    }
}

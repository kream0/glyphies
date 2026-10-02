package app.glyphies.sense

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.glyphies.Graph
import app.glyphies.engine.Buzz

/** Short vibrations for game events: you're holding the phone, so you feel the hits. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun buzz(kind: Buzz) {
        if (!Graph.settings.current.haptics) return
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = when (kind) {
            Buzz.TICK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(12, 90)
            }
            Buzz.HIT -> VibrationEffect.createOneShot(70, 220)
            Buzz.BIG -> VibrationEffect.createWaveform(longArrayOf(0, 90, 70, 200), intArrayOf(0, 255, 0, 255), -1)
            Buzz.WIN -> VibrationEffect.createWaveform(longArrayOf(0, 35, 60, 35, 60, 80), intArrayOf(0, 160, 0, 160, 0, 255), -1)
        }
        runCatching { v.vibrate(effect) }
    }
}

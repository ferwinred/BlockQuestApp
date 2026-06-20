// =====================================================================
// HapticManager.kt
// Block Quest — Haptic feedback wrapper (Semana 4)
//
// Design decisions:
//  • Uses VibratorManager (API 31+) with a Vibrator fallback for
//    API 26–30.  Below API 26 haptics are silently skipped.
//  • Each feedback type is a named enum so the caller never
//    touches raw millisecond arrays — easy to tune later.
//  • Haptics are disabled if the user turns them off in Settings.
//    The toggle is in sync with SoundManager for a single
//    "Audio & Haptics" settings row.
// =====================================================================

package com.blockquest.audio

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Named haptic patterns for each in-game action. */
enum class HapticPattern {
    /** Short click when a piece is placed successfully. */
    PIECE_PLACE,
    /** Medium pulse when a line/column/square clears. */
    LINE_CLEAR,
    /** Double-pulse for a combo activation. */
    COMBO,
    /** Long rumble for game over. */
    GAME_OVER,
    /** Ascending waveform for level complete. */
    LEVEL_COMPLETE,
    /** Short tick for heat-unlock feedback. */
    HEAT_UNLOCK,
}

@Singleton
class HapticManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    // Resolve a Vibrator regardless of API level.
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrate(pattern: HapticPattern) {
        if (!_hapticsEnabled.value) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val effect = when (pattern) {
            HapticPattern.PIECE_PLACE     -> VibrationEffect.createOneShot(30L,  80)
            HapticPattern.LINE_CLEAR      -> VibrationEffect.createOneShot(60L,  200)
            HapticPattern.COMBO           ->
                // Double-pulse: 40 ms on, 40 ms off, 60 ms on
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 40L, 40L, 60L),
                    intArrayOf (0,  200, 0,  255),
                    -1,
                )
            HapticPattern.GAME_OVER       ->
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 100L, 60L, 100L),
                    intArrayOf (0,  150,  0,   200),
                    -1,
                )
            HapticPattern.LEVEL_COMPLETE  ->
                // Ascending triple tap
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 50L, 50L, 70L, 50L, 90L),
                    intArrayOf (0,  100, 0,   160, 0,   255),
                    -1,
                )
            HapticPattern.HEAT_UNLOCK     -> VibrationEffect.createOneShot(20L, 60)
        }
        vibrator?.vibrate(effect)
    }

    fun setEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
    }
}

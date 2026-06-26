// =====================================================================
// SoundManager.kt
// Block Quest — SoundPool wrapper (Semana 4)
//
// Design decisions:
//  • SoundPool over MediaPlayer: lower latency for short game SFX.
//  • All sounds are loaded once at construction; play() is a cheap
//    integer lookup — safe to call on the main thread.
//  • Sound is disabled when DataStore "soundEnabled" is false.
//    The toggle is exposed as a MutableStateFlow so the UI can
//    observe it reactively without re-creating the SoundPool.
//  • SoundPool is released in release() which is called from the
//    SoundModule singleton scope (i.e. when the process exits).
// =====================================================================

package com.blockquest.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import com.blockquest.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Maps each in-game action to the correct raw resource and volume. */
enum class Sfx(
    val resId: Int,
    val leftVol: Float  = 1f,
    val rightVol: Float = 1f,
    val priority: Int   = 1,
    val loop: Int       = 0,
    val rate: Float     = 1f,
) {
    PIECE_PLACE   (R.raw.sfx_piece_place,    0.7f, 0.7f),
    LINE_CLEAR    (R.raw.sfx_line_clear,     1f,   1f),
    COMBO         (R.raw.sfx_combo_activate, 1f,   1f),
    STREAK_HIGH   (R.raw.sfx_streak_high,    0.9f, 0.9f),
    GAME_OVER     (R.raw.sfx_game_over,      1f,   1f),
    LEVEL_COMPLETE(R.raw.sfx_level_complete, 1f,   1f),
    HEAT_UNLOCK   (R.raw.sfx_heat_unlock,    0.8f, 0.8f),
}

@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Sound-on/off toggle — persisted through DataStore by the caller.
    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // sound-id map populated after load completes.
    private val ids = mutableMapOf<Sfx, Int>()
    private val loadedIds = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedIds.add(sampleId)
            }
        }
        
        Sfx.entries.forEach { sfx ->
            try {
                val id = pool.load(context, sfx.resId, sfx.priority)
                if (id != 0) {
                    ids[sfx] = id
                }
            } catch (e: Exception) {
                // Ignore load errors for now, play() handles missing ids
            }
        }
    }

    /** Play a sound effect. No-ops when sound is disabled or not yet loaded. */
    fun play(sfx: Sfx) {
        if (!_soundEnabled.value) return
        val id = ids[sfx] ?: return
        if (loadedIds.contains(id)) {
            pool.play(id, sfx.leftVol, sfx.rightVol, sfx.priority, sfx.loop, sfx.rate)
        }
    }

    fun setEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        if (!enabled) pool.autoPause()
    }

    /** Call when the singleton is torn down (process exit). */
    fun release() {
        pool.release()
    }
}

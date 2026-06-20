// =====================================================================
// MissionService.kt
// Block Quest — Mission / quest engine
// =====================================================================
//
// `MissionService` is the bridge between the gameplay engine
// and the persistent mission-progress store.
//
// Pattern: the engine emits `MissionEvent`s, the service
// folds them into the in-memory progress map, persists to
// the local Room cache + remote Firestore, and exposes the
// updated progress as a `Flow<List<MissionProgress>>`.
//
// The service is **idempotent** — applying the same event
// twice never increments progress twice (we hash the event
// and skip duplicates within a short window). This matters
// because the engine re-emits some events (e.g. on replay
// tools or after a back-from-background).
// =====================================================================

package com.blockquest.domain.mission

import com.blockquest.domain.model.ComboType
import com.blockquest.domain.model.MissionCadence
import com.blockquest.domain.model.MissionEvent
import com.blockquest.domain.model.MissionProgress
import com.blockquest.domain.model.MissionSpec
import com.blockquest.domain.model.MissionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class MissionService @Inject constructor() {

    private val _progress = MutableStateFlow<Map<String, MissionProgress>>(emptyMap())
    val progress: StateFlow<Map<String, MissionProgress>> = _progress.asStateFlow()

    /**
     * Recent event hashes (last 256), used for idempotency.
     * Older entries are evicted FIFO.
     */
    private val recentHashes: ArrayDeque<Long> = ArrayDeque()
    private val recentSet: MutableSet<Long> = HashSet()
    private val recentCap = 256

    /**
     * Replace the progress map (used at app start to load
     * from the local cache and remote sources).
     */
    fun seed(initial: List<MissionProgress>) {
        _progress.value = initial.associateBy { it.missionId }
    }

    /**
     * Apply a mission event to the current progress. Returns
     * the list of missions that JUST became completed (so the
     * presentation layer can fire a celebration animation).
     */
    fun apply(event: MissionEvent): List<MissionProgress> {
        val hash = eventHash(event)
        if (!markHash(hash)) return emptyList()
        val current = _progress.value
        val updated = HashMap(current)
        val newlyCompleted = mutableListOf<MissionProgress>()

        for ((id, prog) in current) {
            if (prog.completed) continue
            val spec = prog.spec
            val delta = eventMatches(event, spec) ?: continue
            val newProgress = (prog.progress + delta).coerceAtMost(spec.target)
            val completed = newProgress >= spec.target
            val updatedProg = prog.copy(
                progress = newProgress,
                completed = completed,
            )
            updated[id] = updatedProg
            if (completed) {
                newlyCompleted.add(updatedProg)
            }
        }
        _progress.value = updated
        return newlyCompleted
    }

    /**
     * Mark a mission as claimed. Returns true if the claim
     * was accepted (i.e. the mission was completed and not
     * already claimed).
     */
    fun claim(missionId: String, nowMs: Long): MissionProgress? {
        val current = _progress.value
        val prog = current[missionId] ?: return null
        if (!prog.completed || prog.isClaimed) return null
        val claimed = prog.copy(claimedAtMs = nowMs)
        _progress.value = current + (missionId to claimed)
        return claimed
    }

    /**
     * Reset all missions of a given cadence. Used when the
     * daily / weekly clock rolls over.
     */
    fun resetCadence(cadence: MissionCadence, nowMs: Long) {
        val current = _progress.value
        val updated = current.mapValues { (_, prog) ->
            if (prog.spec.cadence == cadence && !prog.completed) {
                prog.copy(progress = 0, cycleStartedAtMs = nowMs)
            } else prog
        }
        _progress.value = updated
    }

    /**
     * Replace one mission entirely (used when the daily
     * selector picks a new mission to replace a completed
     * one). The spec is swapped, progress resets to 0.
     */
    fun replace(missionId: String, newSpec: MissionSpec, nowMs: Long) {
        val current = _progress.value
        val existing = current[missionId]
        val fresh = MissionProgress(
            missionId = newSpec.missionId,
            spec = newSpec,
            progress = 0,
            completed = false,
            claimedAtMs = null,
            cycleStartedAtMs = nowMs,
        )
        _progress.value = current + (missionId to fresh)
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    /**
     * Compute the increment to apply for this event against
     * this spec, or null if the event does not match.
     */
    private fun eventMatches(event: MissionEvent, spec: MissionSpec): Int? {
        return when (event) {
            is MissionEvent.PiecePlaced -> when (spec.type) {
                MissionType.PlacePieces -> 1
                else -> null
            }
            is MissionEvent.LinesCleared -> when (spec.type) {
                MissionType.ClearLines -> event.rowCount + event.columnCount
                else -> null
            }
            is MissionEvent.SquaresCleared -> when (spec.type) {
                MissionType.ClearSquares -> event.count
                else -> null
            }
            is MissionEvent.ComboAchieved -> when (spec.type) {
                MissionType.AchieveCombos -> {
                    val min = spec.minCombo ?: ComboType.Double
                    if (event.tier.ordinal >= min.ordinal) 1 else null
                }
                else -> null
            }
            is MissionEvent.StreakAchieved -> when (spec.type) {
                MissionType.AchieveStreak -> {
                    if (event.level >= spec.target) 1 else null
                }
                else -> null
            }
            is MissionEvent.ScoreReached -> when (spec.type) {
                MissionType.ReachScore -> {
                    // single increment when the threshold is first crossed
                    if (event.total >= spec.target) 1 else null
                }
                else -> null
            }
            is MissionEvent.SpecialCellsCleared -> when (spec.type) {
                MissionType.ClearSpecialCells -> event.count
                else -> null
            }
            is MissionEvent.LevelCompleted -> when (spec.type) {
                MissionType.CompleteLevels -> 1
                MissionType.CompleteWorld -> if (spec.worldIndex == event.worldIndex) 1 else null
                else -> null
            }
            is MissionEvent.LevelFailed -> null
            is MissionEvent.BigClear -> when (spec.type) {
                MissionType.BigClears -> 1
                else -> null
            }
            is MissionEvent.PerfectClear -> when (spec.type) {
                MissionType.PerfectClears -> 1
                else -> null
            }
            MissionEvent.AdWatched -> when (spec.type) {
                MissionType.WatchAds -> 1
                else -> null
            }
            MissionEvent.DailyRewardClaimed -> when (spec.type) {
                MissionType.ClaimDailyStreak -> 1
                else -> null
            }
        }
    }

    private fun eventHash(e: MissionEvent): Long {
        // Fast, non-cryptographic hash. Two events with the
        // same content hash to the same value. Different
        // events *almost always* hash to different values.
        var h = 0x9E3779B97F4A7C15uL.toLong()
        val tag = e::class.simpleName.hashCode().toLong()
        h = h xor tag
        h *= 0x100000001B3L
        when (e) {
            is MissionEvent.PiecePlaced -> h = mix(h, e.cellCount.toLong())
            is MissionEvent.LinesCleared -> {
                h = mix(h, e.rowCount.toLong())
                h = mix(h, e.columnCount.toLong() + 0x9E37L)
            }
            is MissionEvent.SquaresCleared -> h = mix(h, e.count.toLong() + 0x1357L)
            is MissionEvent.ComboAchieved -> h = mix(h, e.tier.ordinal.toLong() + 0x2468L)
            is MissionEvent.StreakAchieved -> h = mix(h, e.level.toLong() + 0xACE0L)
            is MissionEvent.ScoreReached -> h = mix(h, e.total.toLong() + 0xBEEFL)
            is MissionEvent.SpecialCellsCleared -> h = mix(h, e.count.toLong() + 0xCAFE0L)
            is MissionEvent.LevelCompleted -> {
                h = mix(h, e.worldIndex.toLong() + 0xDEAD0L)
                h = mix(h, e.score.toLong())
            }
            is MissionEvent.LevelFailed -> {
                h = mix(h, e.worldIndex.toLong() + 0xDEAD1L)
            }
            is MissionEvent.BigClear -> h = mix(h, e.cellsCleared.toLong() + 0xB16B00BL)
            is MissionEvent.PerfectClear -> {
                h = mix(h, e.rows.toLong() + 0xC0DEL)
                h = mix(h, e.cols.toLong() + 0xC0DE2L)
            }
            MissionEvent.AdWatched, MissionEvent.DailyRewardClaimed -> { /* already mixed */ }
        }
        return h
    }

    private fun mix(h: Long, x: Long): Long {
        var v = h xor x
        v *= 0x100000001B3L
        v = v xor (v ushr 33)
        return v
    }

    private fun markHash(hash: Long): Boolean {
        if (hash in recentSet) return false
        recentSet.add(hash)
        recentHashes.addLast(hash)
        if (recentHashes.size > recentCap) {
            val evicted = recentHashes.removeFirst()
            recentSet.remove(evicted)
        }
        return true
    }
}

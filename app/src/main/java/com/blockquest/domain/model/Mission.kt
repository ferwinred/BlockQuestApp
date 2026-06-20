// =====================================================================
// Mission.kt
// Block Quest — Domain models: missions / quests
// =====================================================================
//
// A Mission is a self-contained objective with a reward. The
// game tracks ~15 simultaneous missions at any time:
//   * 3 daily missions (refresh at 00:00 UTC)
//   * 5 weekly missions (refresh on Monday)
//   * 7 one-shot achievement missions (never reset)
//
// `MissionService` is the brain: it consumes engine events and
// updates progress. The presentation layer just renders the
// state — no business logic in the UI.
// =====================================================================

package com.blockquest.domain.model

/**
 * The type of mission. The engine emits events tagged with
 * the relevant type, and `MissionService` maps events →
 * progress updates.
 */
enum class MissionType {
    /** Place N pieces (regardless of shape). */
    PlacePieces,

    /** Clear N rows OR columns (any combination). */
    ClearLines,

    /** Clear N 3x3 squares specifically. */
    ClearSquares,

    /** Achieve N combos of `minCombo` tier or higher. */
    AchieveCombos,

    /** Reach a streak of N (consecutive scoring moves). */
    AchieveStreak,

    /** Score a total of N points in a single level. */
    ReachScore,

    /** Clear N special cells (crystal / heat-lock / black-hole). */
    ClearSpecialCells,

    /** Complete N distinct levels. */
    CompleteLevels,

    /** Achieve N pieces placed with a combo of tier `minCombo`+. */
    ComboPlacement,

    /** Claim the daily reward N days in a row. */
    ClaimDailyStreak,

    /** Complete every level in world `worldIndex`. */
    CompleteWorld,

    /** Watch N rewarded ads. */
    WatchAds,

    /** Trigger N "5-piece clears" (5+ cells cleared in one move). */
    BigClears,

    /** Clear N lines AND a 3x3 in the same move. */
    PerfectClears,
}

enum class MissionCadence {
    Daily,        // resets at 00:00 UTC
    Weekly,       // resets Monday 00:00 UTC
    Achievement,  // never resets, one-shot
}

/**
 * Static definition of a mission. Immutable. Loaded from
 * Firestore (or from a bundled JSON in offline mode).
 */
data class MissionSpec(
    val missionId: String,
    val type: MissionType,
    val target: Int,
    val minCombo: ComboType? = null,
    val worldIndex: Int? = null,
    val cadence: MissionCadence = MissionCadence.Achievement,
    val description: String,
    val rewardCoins: Int = 0,
    val rewardGems: Int = 0,
    val rewardSkinId: String? = null,
    val rewardTitleId: String? = null,
    val weight: Int = 1,  // used by the daily selector
) {
    init {
        require(target > 0) { "target must be positive" }
        require(description.isNotBlank()) { "description required" }
    }
}

/**
 * Per-player progress on a mission. Mirrors `MissionProgress`
 * in the C# port, but flattened (spec inlined for cache
 * efficiency).
 */
data class MissionProgress(
    val missionId: String,
    val spec: MissionSpec,
    val progress: Int,
    val completed: Boolean = false,
    val claimedAtMs: Long? = null,
    val cycleStartedAtMs: Long = 0L,
) {
    val isClaimed: Boolean get() = claimedAtMs != null
    val fraction: Float
        get() = (progress.toFloat() / spec.target).coerceIn(0f, 1f)
    val remaining: Int get() = (spec.target - progress).coerceAtLeast(0)
}

/**
 * One-shot engine event that the mission system subscribes
 * to. The `MissionService` listens to the engine's
 * `SharedFlow<GameEvent>` and routes events into the
 * appropriate mission counters.
 */
sealed class MissionEvent {
    data class PiecePlaced(val cellCount: Int) : MissionEvent()
    data class LinesCleared(val rowCount: Int, val columnCount: Int) : MissionEvent()
    data class SquaresCleared(val count: Int) : MissionEvent()
    data class ComboAchieved(val tier: ComboType) : MissionEvent()
    data class StreakAchieved(val level: Int) : MissionEvent()
    data class ScoreReached(val total: Int) : MissionEvent()
    data class SpecialCellsCleared(val count: Int) : MissionEvent()
    data class LevelCompleted(val worldIndex: Int, val score: Int, val stars: Int) : MissionEvent()
    data class LevelFailed(val worldIndex: Int, val finalScore: Int) : MissionEvent()
    data class BigClear(val cellsCleared: Int) : MissionEvent()  // 5+ in one move
    data class PerfectClear(val rows: Int, val cols: Int, val squares: Int) : MissionEvent()
    object AdWatched : MissionEvent()
    object DailyRewardClaimed : MissionEvent()
}

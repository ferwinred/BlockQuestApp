// =====================================================================
// UseCases.kt
// Block Quest — Domain use cases (Clean Architecture)
// =====================================================================
//
// Each use case is a single, focused business operation. ViewModels
// in the presentation layer call use cases instead of repositories
// directly. This keeps ViewModels small and makes the use case
// layer easy to test in isolation.
//
// Naming convention: `<Verb><Noun>UseCase`. The class has a
// single `operator fun invoke(...)` so the call site reads as a
// verb (e.g. `placePieceUseCase(level, shape, origin)`).
// =====================================================================

package com.blockquest.domain.usecase

import com.blockquest.domain.mission.MissionService
import com.blockquest.domain.model.CurrencyState
import com.blockquest.domain.model.DailyRewardClaimed
import com.blockquest.domain.model.LevelResult
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.MissionEvent
import com.blockquest.domain.model.MissionProgress
import com.blockquest.domain.model.MissionSpec
import com.blockquest.domain.model.ProgressionState
import com.blockquest.domain.model.WorldDefinition
import com.blockquest.domain.repository.AnalyticsRepository
import com.blockquest.domain.repository.DailyRewardConfigRepository
import com.blockquest.domain.repository.LevelRepository
import com.blockquest.domain.repository.MissionRepository
import com.blockquest.domain.repository.PlayerRepository
import com.blockquest.domain.repository.ProgressionRepository
import com.blockquest.domain.repository.WorldRepository
import com.blockquest.domain.scoring.DailyRewardService
import com.blockquest.domain.scoring.LevelRewardService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject


class GetLevelUseCase @Inject constructor (private val levels: LevelRepository) {
    suspend operator fun invoke(levelId: String): LevelSpec? =
        levels.getLevel(levelId)
}

class ObserveAllLevelsUseCase @Inject constructor (private val levels: LevelRepository) {
    operator fun invoke(): Flow<List<LevelSpec>> = levels.observeAllLevels()
}

class CompleteLevelUseCase @Inject constructor (
    private val progression: ProgressionRepository,
    private val players: PlayerRepository,
    private val analytics: AnalyticsRepository,
    private val leaderboard: com.blockquest.domain.repository.LeaderboardRepository
) {
    /**
     * Persist the result of a level attempt and grant the
     * configured rewards. Idempotent on the result side (re-running
     * with the same score does not give the player a second windfall
     * — the player's "best score" is preserved).
     */
    suspend operator fun invoke(
        levelId: String,
        finalScore: Int,
        stars: Int,
        rewardCoins: Int,
        rewardGems: Int,
    ) {
        val currentProgress = kotlinx.coroutines.flow.first(progression.observeLevelProgress(levelId))
        progression.recordLevelResult(
            LevelResult(levelId, completed = true, stars = stars, bestScore = finalScore, attempts = 1)
        )
        if (rewardCoins > 0) players.addCoins(rewardCoins, "level_complete:$levelId")
        if (rewardGems > 0) players.addGems(rewardGems, "level_complete:$levelId")
        analytics.logEvent(
            "level_complete",
            mapOf("level_id" to levelId, "score" to finalScore, "stars" to stars),
        )
        if (currentProgress == null || finalScore > currentProgress.bestScore) {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val displayName = auth.currentUser?.displayName ?: "Jugador"
            leaderboard.submitScore(levelId, finalScore, displayName)
        }
    }
}

class UnlockWorldUseCase(
    private val progression: ProgressionRepository,
    private val analytics: AnalyticsRepository,
) {
    suspend operator fun invoke(worldIndex: Int, method: String) {
        progression.unlockWorld(worldIndex, method)
        analytics.logEvent(
            "world_unlock",
            mapOf("world_index" to worldIndex, "method" to method),
        )
    }
}

class ObserveCurrencyUseCase @Inject constructor (private val players: PlayerRepository) {
    operator fun invoke(): Flow<CurrencyState> = players.observeCurrency()
}

class SpendCoinsUseCase(
    private val players: PlayerRepository,
    private val analytics: AnalyticsRepository,
) {
    /**
     * Returns true on success, false on insufficient balance.
     */
    suspend operator fun invoke(amount: Int, destination: String): Boolean {
        val ok = players.trySpendCoins(amount, destination)
        if (ok) {
            analytics.logEvent(
                "currency_spent",
                mapOf("currency" to "coins", "amount" to amount, "destination" to destination),
            )
        }
        return ok
    }
}

class ClaimDailyRewardUseCase @Inject constructor (
    private val players: PlayerRepository,
    private val analytics: AnalyticsRepository,
) {
    /**
     * Returns the just-claimed reward, or null if the calendar
     * has not rolled over yet.
     */
    suspend operator fun invoke(): DailyRewardClaimed? {
        val claimed = players.claimDailyReward() ?: return null
        analytics.logEvent(
            "daily_reward_claimed",
            mapOf("day_number" to claimed.newStreak, "coins" to claimed.coins, "gems" to claimed.gems),
        )
        return claimed
    }
}

class EnsureSignedInUseCase @Inject constructor (
    private val players: PlayerRepository,
    private val analytics: AnalyticsRepository,
) {
    suspend operator fun invoke(): String {
        val userId = players.ensureSignedIn()
        analytics.setUserProperty("player_uid", userId)
        return userId
    }
}

class ResetProgressUseCase(
    private val progression: ProgressionRepository,
    private val analytics: AnalyticsRepository,
) {
    suspend operator fun invoke() {
        progression.reset()
        analytics.logEvent("progress_reset")
    }
}

// =====================================================================
// Mission use cases
// =====================================================================

class ObserveMissionsUseCase @Inject constructor (
    private val missions: MissionRepository,
) {
    operator fun invoke(): Flow<List<MissionProgress>> = missions.observeProgress()
}

class ClaimMissionRewardUseCase @Inject constructor (
    private val missions: MissionRepository,
    private val players: PlayerRepository,
    private val analytics: AnalyticsRepository,
) {
    /**
     * Claim a completed mission's reward. Awards coins/gems
     * to the player and marks the mission as claimed in the
     * local + remote store. Returns the claimed progress or
     * null if the mission wasn't claimable.
     */
    suspend operator fun invoke(missionId: String): MissionProgress? {
        val now = System.currentTimeMillis()
        val claimed = missions.markClaimed(missionId, now) ?: return null
        if (claimed.spec.rewardCoins > 0) {
            players.addCoins(claimed.spec.rewardCoins, "mission:${missionId}")
        }
        if (claimed.spec.rewardGems > 0) {
            players.addGems(claimed.spec.rewardGems, "mission:${missionId}")
        }
        analytics.logEvent(
            "mission_claimed",
            mapOf(
                "mission_id" to missionId,
                "reward_coins" to claimed.spec.rewardCoins,
                "reward_gems" to claimed.spec.rewardGems,
            ),
        )
        return claimed
    }
}

class ProcessMissionEventUseCase @Inject constructor (
    private val service: MissionService,
    private val repo: MissionRepository,
) {
    /**
     * Apply a mission event to the in-memory service, then
     * persist any updated progress rows to the repository
     * (which is debounced inside). Returns the list of
     * missions that JUST became completed.
     */
    suspend operator fun invoke(event: MissionEvent): List<MissionProgress> {
        val newlyCompleted = service.apply(event)
        // Persist the updated progress.
        service.progress.value.values.forEach { repo.saveProgress(it) }
        return newlyCompleted
    }
}

class BootstrapMissionsUseCase @Inject constructor (
    private val missions: MissionRepository,
) {
    /**
     * On first launch, seed the active mission set.
     */
    suspend operator fun invoke(): List<MissionProgress> {
        val initial = missions.bootstrapForNewPlayer()
        return initial
    }
}

class RollDailyMissionsUseCase @Inject constructor (
    private val missions: MissionRepository,
) {
    suspend operator fun invoke(): List<MissionProgress> =
        missions.rollCycle(System.currentTimeMillis(), isDaily = true)
}

class RollWeeklyMissionsUseCase @Inject constructor (
    private val missions: MissionRepository,
) {
    suspend operator fun invoke(): List<MissionProgress> =
        missions.rollCycle(System.currentTimeMillis(), isDaily = false)
}

// =====================================================================
// World use cases
// =====================================================================

class ObserveWorldsUseCase @Inject constructor (
    private val worlds: WorldRepository,
) {
    operator fun invoke(): Flow<List<WorldDefinition>> = worlds.observeWorlds()
}

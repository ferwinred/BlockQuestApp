// =====================================================================
// Repositories.kt
// Block Quest — Repository interfaces (Clean Architecture, domain)
// =====================================================================
//
// All repositories are pure interfaces. The data layer in
// `com.blockquest.app.data` provides concrete implementations:
//   * FirestoreLevelRepository    (remote = source of truth)
//   * RoomLevelCache               (local cache for offline play)
//   * DataStorePlayerRepository   (player profile + currency)
//
// Presentation never imports from `data/`. Data may depend on
// domain but not on presentation. Hilt wires everything in `di/`.
// =====================================================================

package com.blockquest.domain.repository

import com.blockquest.domain.model.CosmeticCatalog
import com.blockquest.domain.model.CosmeticInventory
import com.blockquest.domain.model.CurrencyState
import com.blockquest.domain.model.DailyRewardConfig
import com.blockquest.domain.model.DailyRewardState
import com.blockquest.domain.model.EquipResult
import com.blockquest.domain.model.LevelResult
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.MissionProgress
import com.blockquest.domain.model.MissionSpec
import com.blockquest.domain.model.ProgressionState
// import com.blockquest.domain.model.Skin
// import com.blockquest.domain.model.Title
import com.blockquest.domain.model.WorldDefinition
import dagger.Provides
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Source-of-truth for level data. Implementations are expected
 * to cache aggressively: a casual player might restart the same
 * level 10 times in a session, and Firestore is too slow for
 * sub-100ms loads.
 */

interface LevelRepository {
    /**
     * Reactive stream of all level definitions. The first emission
     * is the local cache; subsequent emissions are updates from
     * the remote source. Consumers (the WorldMap) bind once and
     * forget.
     */
    fun observeAllLevels(): Flow<List<LevelSpec>>

    /**
     * One-shot fetch of a single level. Prefers the local cache;
     * falls back to Firestore on miss.
     */
    suspend fun getLevel(levelId: String): LevelSpec?
}

interface ProgressionRepository {
    /**
     * Stream of the current player's progress. The first emission
     * is loaded synchronously from local cache.
     */
    fun observeProgression(): Flow<ProgressionState>

    /**
     * Record the outcome of a level attempt. Writes to Firestore
     * (best-effort) and updates the local cache atomically.
     */
    suspend fun recordLevelResult(result: LevelResult)

    /**
     * Persist that `worldIndex` was just unlocked.
     */
    suspend fun unlockWorld(worldIndex: Int, method: String)

    /**
     * Add `achievementId` to the completed-achievements set.
     */
    suspend fun unlockAchievement(achievementId: String)

    /**
     * Reset all progress (used by the "delete account" / "start over"
     * settings flow).
     */
    suspend fun reset()
}

interface PlayerRepository {
    /**
     * Stream of currency balance.
     */
    fun observeCurrency(): Flow<CurrencyState>

    /**
     * Award coins. `source` is a free-form tag like
     * "level_complete", "iap_pack_welcome", etc. — used by
     * analytics.
     */
    suspend fun addCoins(amount: Int, source: String)

    /**
     * Spend coins. Returns false if the balance was insufficient.
     */
    suspend fun trySpendCoins(amount: Int, destination: String): Boolean

    suspend fun addGems(amount: Int, source: String)
    suspend fun trySpendGems(amount: Int, destination: String): Boolean

    fun observeDailyReward(): Flow<DailyRewardState>
    suspend fun claimDailyReward(): com.blockquest.domain.model.DailyRewardClaimed?

    /**
     * Sign in anonymously (or via the player's existing
     * credential). Idempotent. Returns the resulting user id.
     */
    suspend fun ensureSignedIn(): String
}

interface AnalyticsRepository {
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())
    fun setUserProperty(name: String, value: String)
}

interface RemoteConfigRepository {
    /**
     * Fetch the latest remote-config values. Cached values are
     * served first; the result is `true` if the fetch actually
     * succeeded (vs. served-from-cache).
     */
    suspend fun fetchAndActivate(): Boolean

    /**
     * Get a string config value, with a local default. Thread-safe.
     */
    fun getString(key: String, default: String): String
    fun getInt(key: String, default: Int): Int
    fun getFloat(key: String, default: Float): Float
    fun getBool(key: String, default: Boolean): Boolean
}

/**
 * World catalogue. Five static worlds. We only need to read.
 */
interface WorldRepository {
    fun observeWorlds(): Flow<List<WorldDefinition>>
    suspend fun getWorld(worldIndex: Int): WorldDefinition?
}

/**
 * Mission catalogue + per-player progress.
 *
 * Implementations are responsible for:
 *  * Reading the catalogue (Firestore + Room cache).
 *  * Loading the player's progress for the active cycles
 *    (daily / weekly / achievement).
 *  * Persisting progress updates (debounced — mission
 *    events fire frequently, and we don't want to write
 *    Firestore on every event).
 */
interface MissionRepository {
    /** Stream of all mission specs (catalogue). */
    fun observeAllMissions(): Flow<List<MissionSpec>>

    /** Stream of the player's progress on every active mission. */
    fun observeProgress(): Flow<List<MissionProgress>>

    /** Replace progress for one mission (after the service mutates it). */
    suspend fun saveProgress(progress: MissionProgress)

    /**
     * Reseed daily/weekly missions: the service clears the
     * progress for the old cycle and inserts the new catalogue.
     * Returns the new progress list.
     */
    suspend fun rollCycle(nowMs: Long, isDaily: Boolean): List<MissionProgress>

    /**
     * Mark a mission's reward as claimed. Returns the updated
     * progress, or null if the mission is not claimable.
     */
    suspend fun markClaimed(missionId: String, nowMs: Long): MissionProgress?

    /**
     * Seed the active mission list for a new install: takes
     * the 3 daily + 5 weekly + 7 achievement missions from
     * the catalogue and returns the initial progress rows.
     */
    suspend fun bootstrapForNewPlayer(): List<MissionProgress>
}

/**
 * Daily reward configuration. Lives in Firestore and is
 * refreshed periodically by `RemoteConfigRepository`.
 *
 * The repository returns a `Flow` so the presentation layer
 * can react to config updates (e.g. a Christmas special
 * cycle that marketing flips on the server).
 */
interface DailyRewardConfigRepository {
    fun observeConfig(): Flow<DailyRewardConfig>
    suspend fun currentConfig(): DailyRewardConfig
}

/**
 * Cosmetic ownership + active selection.
 *
 * The catalog (all available skins and titles) is mostly
 * static — the same for every player. The inventory is
 * per-player and is what the store UI binds to.
 */
interface CosmeticRepository {
    /** Stream of the full catalog (skins + titles). */
    fun observeCatalog(): Flow<CosmeticCatalog>

    /** Stream of the player's inventory (owned + active). */
    fun observeInventory(): Flow<CosmeticInventory>

    /**
     * Mark a cosmetic as owned. Called when the player
     * earns a skin/title through gameplay. Idempotent.
     */
    suspend fun grant(cosmeticId: String)

    /** Equip a skin. Fails if the skin is not owned. */
    suspend fun equipSkin(skinId: String): EquipResult

    /** Equip a title (or pass null to clear it). */
    suspend fun equipTitle(titleId: String?): EquipResult
}

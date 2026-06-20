// =====================================================================
// MissionProgressEntity.kt
// Block Quest — Room cache for mission progress
// =====================================================================

package com.blockquest.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.blockquest.domain.model.ComboType
import com.blockquest.domain.model.MissionCadence
import com.blockquest.domain.model.MissionProgress
import com.blockquest.domain.model.MissionSpec
import com.blockquest.domain.model.MissionType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "mission_progress")
data class MissionProgressEntity(
    @PrimaryKey val missionId: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "target") val target: Int,
    @ColumnInfo(name = "min_combo") val minCombo: String?,
    @ColumnInfo(name = "world_index") val worldIndex: Int?,
    @ColumnInfo(name = "cadence") val cadence: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "reward_coins") val rewardCoins: Int,
    @ColumnInfo(name = "reward_gems") val rewardGems: Int,
    @ColumnInfo(name = "reward_skin_id") val rewardSkinId: String?,
    @ColumnInfo(name = "reward_title_id") val rewardTitleId: String?,
    @ColumnInfo(name = "weight") val weight: Int,
    @ColumnInfo(name = "progress") val progress: Int,
    @ColumnInfo(name = "completed") val completed: Boolean,
    @ColumnInfo(name = "claimed_at_ms") val claimedAtMs: Long?,
    @ColumnInfo(name = "cycle_started_at_ms") val cycleStartedAtMs: Long,
)

@Dao
interface MissionProgressDao {
    @Query("SELECT * FROM mission_progress")
    fun observeAll(): Flow<List<MissionProgressEntity>>

    @Query("SELECT * FROM mission_progress")
    suspend fun getAll(): List<MissionProgressEntity>

    @Query("SELECT * FROM mission_progress WHERE missionId = :id LIMIT 1")
    suspend fun getById(id: String): MissionProgressEntity?

    @Query("SELECT * FROM mission_progress WHERE cadence = :cadence")
    suspend fun getByCadence(cadence: String): List<MissionProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MissionProgressEntity)

    @Query("DELETE FROM mission_progress WHERE missionId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM mission_progress")
    suspend fun clear()

    suspend fun upsert(entity: MissionProgressEntity) {
        insert(entity)
    }
}

fun MissionProgress.asEntity(): MissionProgressEntity = MissionProgressEntity(
    missionId = missionId,
    type = spec.type.name,
    target = spec.target,
    minCombo = spec.minCombo?.name,
    worldIndex = spec.worldIndex,
    cadence = spec.cadence.name,
    description = spec.description,
    rewardCoins = spec.rewardCoins,
    rewardGems = spec.rewardGems,
    rewardSkinId = spec.rewardSkinId,
    rewardTitleId = spec.rewardTitleId,
    weight = spec.weight,
    progress = progress,
    completed = completed,
    claimedAtMs = claimedAtMs,
    cycleStartedAtMs = cycleStartedAtMs,
)

fun MissionProgressEntity.asDomain(): MissionProgress {
    val spec = MissionSpec(
        missionId = missionId,
        type = runCatching { MissionType.valueOf(type) }
            .getOrDefault(MissionType.PlacePieces),
        target = target,
        minCombo = minCombo?.let { runCatching { ComboType.valueOf(it) }.getOrNull() },
        worldIndex = worldIndex,
        cadence = runCatching { MissionCadence.valueOf(cadence) }
            .getOrDefault(MissionCadence.Achievement),
        description = description,
        rewardCoins = rewardCoins,
        rewardGems = rewardGems,
        rewardSkinId = rewardSkinId,
        rewardTitleId = rewardTitleId,
        weight = weight,
    )
    return MissionProgress(
        missionId = missionId,
        spec = spec,
        progress = progress,
        completed = completed,
        claimedAtMs = claimedAtMs,
        cycleStartedAtMs = cycleStartedAtMs,
    )
}

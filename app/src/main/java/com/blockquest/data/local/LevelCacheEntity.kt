// =====================================================================
// LevelCacheEntity.kt
// Block Quest — Room schema (level catalogue cache)
// =====================================================================

package com.blockquest.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.LevelObjective
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.LevelType
import com.blockquest.domain.model.PieceShape
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room mirror of a LevelSpec. We denormalize the spec to a
 * single row (JSON-serialized pieces + pre-filled cells) for
 * fast lookups; we accept the cost of serializing once on
 * write in exchange for a one-query read path.
 */
@Entity(tableName = "level_cache")
data class LevelCacheEntity(
    @PrimaryKey val levelId: String,
    @ColumnInfo(name = "level_number") val levelNumber: Int,
    @ColumnInfo(name = "world_index") val worldIndex: Int,
    @ColumnInfo(name = "level_type") val levelType: String,
    @ColumnInfo(name = "target_score") val targetScore: Int,
    @ColumnInfo(name = "time_limit_seconds") val timeLimitSeconds: Float,
    @ColumnInfo(name = "board_size_w") val boardW: Int,
    @ColumnInfo(name = "board_size_h") val boardH: Int,
    @ColumnInfo(name = "pre_filled_json") val preFilledJson: String,
    @ColumnInfo(name = "piece_pool_json") val piecePoolJson: String,
    @ColumnInfo(name = "guaranteed_piece_id") val guaranteedPieceId: String?,
    @ColumnInfo(name = "guaranteed_in_hand") val guaranteedInHand: Int,
    @ColumnInfo(name = "reward_coins") val rewardCoins: Int,
    @ColumnInfo(name = "reward_gems") val rewardGems: Int,
    @ColumnInfo(name = "is_milestone") val isMilestone: Boolean,
    @ColumnInfo(name = "is_boss") val isBoss: Boolean,
    @ColumnInfo(name = "silver_multiplier") val silverMultiplier: Float,
    @ColumnInfo(name = "gold_multiplier") val goldMultiplier: Float,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
)

@Dao
interface LevelCacheDao {

    @Query("SELECT * FROM level_cache")
    fun observeAll(): Flow<List<LevelCacheEntity>>

    @Query("SELECT * FROM level_cache")
    suspend fun getAll(): List<LevelCacheEntity>

    @Query("SELECT * FROM level_cache WHERE levelId = :levelId LIMIT 1")
    suspend fun getById(levelId: String): LevelCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LevelCacheEntity)

    @Query("DELETE FROM level_cache WHERE levelId = :levelId")
    suspend fun deleteById(levelId: String)

    @Query("DELETE FROM level_cache")
    suspend fun clear()

    @Transaction
    suspend fun upsert(entity: LevelCacheEntity) {
        deleteById(entity.levelId)
        insert(entity)
    }
}

// ----------------------------------------------------------------
// Mapping: Domain <-> Entity
// ----------------------------------------------------------------

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class CellJson(val col: Int = 0, val row: Int = 0)

fun LevelSpec.asEntity(): LevelCacheEntity = LevelCacheEntity(
    levelId = levelId,
    levelNumber = levelNumber,
    worldIndex = worldIndex,
    levelType = levelType.name,
    targetScore = targetScore,
    timeLimitSeconds = timeLimitSeconds,
    boardW = boardSize.first,
    boardH = boardSize.second,
    preFilledJson = json.encodeToString(
        ListSerializer(CellJson.serializer()),
        preFilled.map { CellJson(it.col, it.row) }
    ),
    piecePoolJson = json.encodeToString(
        ListSerializer(String.serializer()),
        piecePool.map { it.id }
    ),
    guaranteedPieceId = guaranteedPiece?.id,
    guaranteedInHand = guaranteedInHand,
    rewardCoins = rewardCoins,
    rewardGems = rewardGems,
    isMilestone = isMilestone,
    isBoss = isBoss,
    silverMultiplier = silverMultiplier,
    goldMultiplier = goldMultiplier,
    schemaVersion = 1,
)

fun LevelCacheEntity.asDomain(): LevelSpec? {
    val preFilled: List<Cell> = runCatching {
        json.decodeFromString(
            ListSerializer(CellJson.serializer()),
            preFilledJson
        ).map { Cell(it.col, it.row) }
    }.getOrDefault(emptyList())

    val pieceIds: List<String> = runCatching {
        json.decodeFromString(
            ListSerializer(String.serializer()),
            piecePoolJson
        )
    }.getOrDefault(emptyList())

    val shapePool = pieceIds.mapNotNull { PieceShape.Library[it] }
    if (shapePool.size != pieceIds.size) return null  // unknown shape ids

    return LevelSpec(
        levelId = levelId,
        levelNumber = levelNumber,
        worldIndex = worldIndex,
        levelType = runCatching { LevelType.valueOf(levelType) }
            .getOrDefault(LevelType.Standard),
        objective = LevelObjective.ScoreTarget,
        targetScore = targetScore,
        timeLimitSeconds = timeLimitSeconds,
        piecePool = shapePool,
        preFilled = preFilled,
        guaranteedPiece = guaranteedPieceId?.let { PieceShape.Library[it] },
        guaranteedInHand = guaranteedInHand,
        rewardCoins = rewardCoins,
        rewardGems = rewardGems,
        isMilestone = isMilestone,
        isBoss = isBoss,
        silverMultiplier = silverMultiplier,
        goldMultiplier = goldMultiplier,
    )
}

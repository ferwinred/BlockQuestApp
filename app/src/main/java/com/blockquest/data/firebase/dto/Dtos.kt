// =====================================================================
// Dtos.kt
// Block Quest — Firestore DTOs (data layer)
// =====================================================================
//
// All DTOs are wire-format representations of documents in
// Cloud Firestore. They use only primitive / nullable types and
// are serialization-friendly so we can map them to/from the
// domain model with a single mapper class.
//
// Schema reference
// ----------------
//
//  /players/{userId}
//      displayName: string
//      currency:    { coins: long, gems: long }
//      createdAt:   timestamp
//      lastLevelId: string?
//
//  /players/{userId}/progression
//      results:              { [levelId]: { stars, bestScore, attempts, completed } }
//      worldUnlocked:        [bool, bool, bool, bool, bool]
//      unlockedSkins:        [string]
//      unlockedPowerUps:     [string]
//      completedAchievements: [string]
//
//  /levels/{levelId}
//      levelId, levelNumber, worldIndex, levelType, objective,
//      targetScore, timeLimitSeconds, targetComboCount,
//      boardSize: { width, height },
//      preFilled: [{ col, row }],
//      piecePool: [string],
//      guaranteedPiece: string?,
//      guaranteedInHand: int?,
//      rewardCoins, rewardGems, rewardSkinId, rewardTitleId,
//      isMilestone, isBoss,
//      silverMultiplier, goldMultiplier,
//      bossConfig: { bossId, bossTimeLimitSeconds, requiredSpecialPieceClears, requiredSpecialPieceId },
//      difficultyScore,
//      schemaVersion: int
//
//  /dailyReward/{userId}
//      lastClaimed: timestamp
//      currentStreak: int
// =====================================================================

package com.blockquest.data.firebase.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Date

/**
 * Custom serializer for Firebase Timestamp so it can be used in @Serializable
 * DTOs. We serialize it as its millisecond value (Long).
 */
object TimestampSerializer : KSerializer<Timestamp> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Timestamp", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Timestamp) {
        encoder.encodeLong(value.toDate().time)
    }

    override fun deserialize(decoder: Decoder): Timestamp {
        return Timestamp(Date(decoder.decodeLong()))
    }
}

@Serializable
data class PlayerDto(
    val displayName: String = "Jugador",
    val currency: CurrencyDto = CurrencyDto(),
    @PropertyName("createdAt")
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp? = null,
    val lastLevelId: String? = null,
)

@Serializable
data class CurrencyDto(
    val coins: Long = 0,
    val gems: Long = 0,
    val boosters: Map<String, Int> = emptyMap(),
)

@Serializable
data class ProgressionDto(
    val results: Map<String, LevelResultDto> = emptyMap(),
    val worldUnlocked: List<Boolean> = listOf(true, false, false, false, false),
    val unlockedSkins: List<String> = listOf("default"),
    val unlockedPowerUps: List<String> = emptyList(),
    val completedAchievements: List<String> = emptyList(),
    val schemaVersion: Int = 1,
)

@Serializable
data class LevelResultDto(
    val completed: Boolean = false,
    val stars: Int = 0,
    val bestScore: Int = 0,
    val attempts: Int = 0,
)

@Serializable
data class LevelDto(
    val levelId: String = "",
    val levelNumber: Int = 0,
    val worldIndex: Int = 0,
    val levelType: String = "Standard",
    val objective: String = "ScoreTarget",
    val targetScore: Int = 0,
    val timeLimitSeconds: Double = 0.0,
    val targetComboCount: Int = 0,
    val boardSize: BoardSizeDto = BoardSizeDto(),
    val preFilled: List<CellDto> = emptyList(),
    val piecePool: List<String> = emptyList(),
    val guaranteedPiece: String? = null,
    val guaranteedInHand: Int = -1,
    val rewardCoins: Int = 0,
    val rewardGems: Int = 0,
    val rewardSkinId: String? = null,
    val rewardTitleId: String? = null,
    @get:PropertyName("isMilestone")
    @set:PropertyName("isMilestone")
    var isMilestone: Boolean = false,
    @get:PropertyName("isBoss")
    @set:PropertyName("isBoss")
    var isBoss: Boolean = false,
    val silverMultiplier: Double = 1.25,
    val goldMultiplier: Double = 1.50,
    val bossConfig: BossConfigDto? = null,
    val difficultyScore: Double = 1.0,
    val schemaVersion: Int = 1,
)

@Serializable
data class BoardSizeDto(val width: Int = 8, val height: Int = 8)

@Serializable
data class CellDto(
    val col: Int = 0,
    val row: Int = 0,
    val type: String = "Occupied"
)

@Serializable
data class BossConfigDto(
    val bossId: String = "",
    val bossTimeLimitSeconds: Double = 0.0,
    val requiredSpecialPieceClears: Int = 0,
    val requiredSpecialPieceId: String? = null,
)

@Serializable
data class DailyRewardDto(
    @PropertyName("lastClaimed")
    @Serializable(with = TimestampSerializer::class)
    val lastClaimed: Timestamp? = null,
    val currentStreak: Int = 0,
)

// ----------------------------------------------------------------
// Misions
// ----------------------------------------------------------------

@Serializable
data class MissionDto(
    val missionId: String = "",
    val type: String = "PlacePieces",
    val target: Int = 1,
    val minCombo: String? = null,
    val worldIndex: Int? = null,
    val cadence: String = "Achievement",
    val description: String = "",
    val rewardCoins: Int = 0,
    val rewardGems: Int = 0,
    val rewardSkinId: String? = null,
    val rewardTitleId: String? = null,
    val weight: Int = 1,
    val schemaVersion: Int = 1,
)

@Serializable
data class MissionProgressDto(
    val missionId: String = "",
    val progress: Int = 0,
    val completed: Boolean = false,
    @PropertyName("claimedAt")
    @Serializable(with = TimestampSerializer::class)
    val claimedAt: Timestamp? = null,
    @PropertyName("cycleStartedAt")
    @Serializable(with = TimestampSerializer::class)
    val cycleStartedAt: Timestamp? = null,
)

@Serializable
data class MissionBootstrapDto(
    val playerId: String = "",
    val daily: List<MissionProgressDto> = emptyList(),
    val weekly: List<MissionProgressDto> = emptyList(),
    val achievement: List<MissionProgressDto> = emptyList(),
    @PropertyName("lastDailyRoll")
    @Serializable(with = TimestampSerializer::class)
    val lastDailyRoll: Timestamp? = null,
    @PropertyName("lastWeeklyRoll")
    @Serializable(with = TimestampSerializer::class)
    val lastWeeklyRoll: Timestamp? = null,
    val schemaVersion: Int = 1,
)

// ----------------------------------------------------------------
// Worlds
// ----------------------------------------------------------------

@Serializable
data class WorldDto(
    val worldIndex: Int = 0,
    val worldId: String = "",
    val displayName: String = "",
    val tagline: String = "",
    val themeName: String = "pradera",
    val levelCount: Int = 30,
    val unlockLevelId: String? = null,
    val unlockStarsRequired: Int = 0,
    val ambientMusicId: String? = null,
    val backgroundImageId: String? = null,
    val schemaVersion: Int = 1,
)

// ----------------------------------------------------------------
// Daily reward config (Firestore / Remote Config)
// ----------------------------------------------------------------

@Serializable
data class DailyRewardConfigDto(
    val cycle: List<DailyRewardDayDto> = emptyList(),
    val baseCoins: Int = 50,
    val streakBonusMultiplier: Double = 0.1,
    val cooldownMs: Long = 24L * 60 * 60 * 1000,
    val missResetTo: Int = 1,
    val schemaVersion: Int = 1,
)

@Serializable
data class DailyRewardDayDto(
    val dayNumber: Int = 1,
    val coins: Int = 0,
    val gems: Int = 0,
    val isMilestone: Boolean = false,
    val rewardSkinId: String? = null,
    val rewardTitleId: String? = null,
    val rewardPowerUpId: String? = null,
    val description: String = "",
)

// ----------------------------------------------------------------
// Cosmetics (skins, titles)
// ----------------------------------------------------------------

@Serializable
data class SkinDto(
    val skinId: String = "",
    val displayName: String = "",
    val themeName: String = "pradera",
    val boardTint: Long? = null,
    val particlePreset: String? = null,
    val rarity: String = "Common",
    val source: String = "Default",
    val unlockHint: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class TitleDto(
    val titleId: String = "",
    val displayName: String = "",
    val position: String = "Prefix",
    val text: String = "",
    val rarity: String = "Common",
    val source: String = "Default",
    val unlockHint: String? = null,
    val sortOrder: Int = 0,
)

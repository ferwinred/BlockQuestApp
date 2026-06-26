// =====================================================================
// Mappers.kt
// Block Quest — DTO ↔ Domain mappers
// =====================================================================
//
// Mappers are pure-Kotlin functions (no Firebase import). The
// data layer is the only place that knows the wire format;
// everything above uses the domain model.
// =====================================================================

package com.blockquest.data.firebase.mapper

import com.google.firebase.Timestamp
import java.util.Date

import com.blockquest.data.firebase.dto.BoardSizeDto
import com.blockquest.data.firebase.dto.BossConfigDto
import com.blockquest.data.firebase.dto.CellDto
import com.blockquest.data.firebase.dto.CurrencyDto
import com.blockquest.data.firebase.dto.DailyRewardConfigDto
import com.blockquest.data.firebase.dto.DailyRewardDayDto
import com.blockquest.data.firebase.dto.DailyRewardDto
import com.blockquest.data.firebase.dto.LevelDto
import com.blockquest.data.firebase.dto.LevelResultDto
import com.blockquest.data.firebase.dto.MissionDto
import com.blockquest.data.firebase.dto.MissionProgressDto
import com.blockquest.data.firebase.dto.PlayerDto
import com.blockquest.data.firebase.dto.ProgressionDto
import com.blockquest.data.firebase.dto.SkinDto
import com.blockquest.data.firebase.dto.TitleDto
import com.blockquest.data.firebase.dto.WorldDto
import com.blockquest.data.local.MissionProgressEntity
import com.blockquest.domain.model.BossConfig
import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.ComboType
import com.blockquest.domain.model.CosmeticRarity
import com.blockquest.domain.model.CosmeticSource
import com.blockquest.domain.model.CurrencyState
import com.blockquest.domain.model.DailyRewardConfig
import com.blockquest.domain.model.DailyRewardDay
import com.blockquest.domain.model.DailyRewardState
import com.blockquest.domain.model.LevelObjective
import com.blockquest.domain.model.LevelResult
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.LevelType
import com.blockquest.domain.model.MissionCadence
import com.blockquest.domain.model.MissionProgress
import com.blockquest.domain.model.MissionSpec
import com.blockquest.domain.model.MissionType
import com.blockquest.domain.model.PieceShape
import com.blockquest.domain.model.PlayerState
import com.blockquest.domain.model.ProgressionState
import com.blockquest.domain.model.Skin
import com.blockquest.domain.model.Title
import com.blockquest.domain.model.TitlePosition
import com.blockquest.domain.model.WorldDefinition

// ----------------------------------------------------------------
// Player
// ----------------------------------------------------------------

fun PlayerDto.toDomain(userId: String): PlayerState =
    PlayerState(
        userId = userId,
        displayName = displayName,
        currency = currency.toDomain(),
    )

fun CurrencyDto.toDomain(): CurrencyState =
    CurrencyState(coins = coins.toInt(), gems = gems.toInt())

fun CurrencyState.toDto(): CurrencyDto =
    CurrencyDto(coins = coins.toLong(), gems = gems.toLong())

// ----------------------------------------------------------------
// Progression
// ----------------------------------------------------------------

fun ProgressionDto.toDomain(playerId: String): ProgressionState =
    ProgressionState(
        playerId = playerId,
        displayName = playerId,  // overridden by PlayerDto
        results = results.mapValues { it.value.toDomain(it.key) },
        worldUnlocked = worldUnlocked,
        unlockedSkins = unlockedSkins,
        unlockedPowerUps = unlockedPowerUps,
        completedAchievements = completedAchievements,
    )

fun LevelResultDto.toDomain(levelId: String): LevelResult =
    LevelResult(
        levelId = levelId,
        completed = completed,
        stars = stars,
        bestScore = bestScore,
        attempts = attempts,
    )

fun LevelResult.toDto(): LevelResultDto =
    LevelResultDto(
        completed = completed,
        stars = stars,
        bestScore = bestScore,
        attempts = attempts,
    )

// ----------------------------------------------------------------
// Level
// ----------------------------------------------------------------

fun LevelDto.toDomain(): LevelSpec? {
    val shapePool = piecePool.mapNotNull { id ->
        PieceShape.Library[id]
    }
    if (shapePool.isEmpty() && piecePool.isNotEmpty()) {
        // We were given ids we don't recognise. Refuse to
        // build the spec — the validator will surface this.
        return null
    }
    val guaranteed = guaranteedPiece?.let { id -> PieceShape.Library[id] }
    val bossConfigDto = bossConfig
    val bossConfig = if (isBoss && bossConfigDto != null) {
        BossConfig(
            bossId = bossConfigDto.bossId,
            bossTimeLimitSeconds = bossConfigDto.bossTimeLimitSeconds.toFloat(),
            requiredSpecialPieceClears = bossConfigDto.requiredSpecialPieceClears,
            requiredSpecialPieceId = bossConfigDto.requiredSpecialPieceId,
        )
    } else null
    return LevelSpec(
        levelId = levelId,
        levelNumber = levelNumber,
        worldIndex = worldIndex,
        levelType = runCatching { LevelType.valueOf(levelType) }
            .getOrDefault(LevelType.Standard),
        objective = runCatching { LevelObjective.valueOf(objective) }
            .getOrDefault(LevelObjective.ScoreTarget),
        targetScore = targetScore,
        timeLimitSeconds = timeLimitSeconds.toFloat(),
        targetComboCount = targetComboCount,
        boardSize = boardSize.width to boardSize.height,
        preFilled = preFilled.map { Cell(it.col, it.row, it.type) },
        piecePool = shapePool,
        guaranteedPiece = guaranteed,
        guaranteedInHand = guaranteedInHand,
        rewardCoins = rewardCoins,
        rewardGems = rewardGems,
        rewardSkinId = rewardSkinId,
        rewardTitleId = rewardTitleId,
        isMilestone = isMilestone,
        isBoss = isBoss,
        silverMultiplier = silverMultiplier.toFloat(),
        goldMultiplier = goldMultiplier.toFloat(),
        bossConfig = bossConfig,
    )
}

// ----------------------------------------------------------------
// Daily reward
// ----------------------------------------------------------------

fun DailyRewardDto.toDomain(): DailyRewardState =
    DailyRewardState(
        lastClaimed = lastClaimed?.toDate()?.time ?: 0L,
        currentStreak = currentStreak
    )

fun DailyRewardState.toDto(): DailyRewardDto =
    DailyRewardDto(
        lastClaimed = Timestamp(Date(lastClaimed)),
        currentStreak = currentStreak
    )

fun DailyRewardConfigDto.toDomain(): DailyRewardConfig = DailyRewardConfig(
    cycle = cycle.map { it.toDomain() },
    baseCoins = baseCoins,
    streakBonusMultiplier = streakBonusMultiplier.toFloat(),
    cooldownMs = cooldownMs,
    missResetTo = missResetTo,
)

fun DailyRewardConfig.toDto(): DailyRewardConfigDto = DailyRewardConfigDto(
    cycle = cycle.map { it.toDto() },
    baseCoins = baseCoins,
    streakBonusMultiplier = streakBonusMultiplier.toDouble(),
    cooldownMs = cooldownMs,
    missResetTo = missResetTo,
)

fun DailyRewardDayDto.toDomain(): DailyRewardDay = DailyRewardDay(
    dayNumber = dayNumber,
    coins = coins,
    gems = gems,
    isMilestone = isMilestone,
    rewardSkinId = rewardSkinId,
    rewardTitleId = rewardTitleId,
    rewardPowerUpId = rewardPowerUpId,
    description = description,
)

fun DailyRewardDay.toDto(): DailyRewardDayDto = DailyRewardDayDto(
    dayNumber = dayNumber,
    coins = coins,
    gems = gems,
    isMilestone = isMilestone,
    rewardSkinId = rewardSkinId,
    rewardTitleId = rewardTitleId,
    rewardPowerUpId = rewardPowerUpId,
    description = description,
)

// ----------------------------------------------------------------
// Missions
// ----------------------------------------------------------------

fun MissionDto.toDomain(): MissionSpec? = runCatching {
    MissionSpec(
        missionId = missionId,
        type = MissionType.valueOf(type),
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
}.getOrNull()

fun MissionSpec.toDto(): MissionDto = MissionDto(
    missionId = missionId,
    type = type.name,
    target = target,
    minCombo = minCombo?.name,
    worldIndex = worldIndex,
    cadence = cadence.name,
    description = description,
    rewardCoins = rewardCoins,
    rewardGems = rewardGems,
    rewardSkinId = rewardSkinId,
    rewardTitleId = rewardTitleId,
    weight = weight,
)

fun MissionProgressDto.toDomain(spec: MissionSpec): MissionProgress = MissionProgress(
    missionId = missionId,
    spec = spec,
    progress = progress,
    completed = completed,
    claimedAtMs = claimedAt?.toDate()?.time,
    cycleStartedAtMs = cycleStartedAt?.toDate()?.time ?: 0L,
)

fun MissionProgress.toDto(): MissionProgressDto = MissionProgressDto(
    missionId = missionId,
    progress = progress,
    completed = completed,
    claimedAt = claimedAtMs?.let { Timestamp(Date(it)) },
    cycleStartedAt = Timestamp(Date(cycleStartedAtMs)),
)

fun MissionProgressEntity.toDto(): MissionProgressDto = MissionProgressDto(
    missionId = missionId,
    progress = progress,
    completed = completed,
    claimedAt = claimedAtMs?.let { Timestamp(Date(it)) },
    cycleStartedAt = Timestamp(Date(cycleStartedAtMs)),
)

// ----------------------------------------------------------------
// Worlds
// ----------------------------------------------------------------

fun WorldDto.toDomain(): WorldDefinition? = runCatching {
    WorldDefinition(
        worldIndex = worldIndex,
        worldId = worldId,
        displayName = displayName,
        tagline = tagline,
        themeName = themeName,
        levelCount = levelCount,
        unlockLevelId = unlockLevelId,
        unlockStarsRequired = unlockStarsRequired,
        ambientMusicId = ambientMusicId,
        backgroundImageId = backgroundImageId,
    )
}.getOrNull()

fun WorldDefinition.toDto(): WorldDto = WorldDto(
    worldIndex = worldIndex,
    worldId = worldId,
    displayName = displayName,
    tagline = tagline,
    themeName = themeName,
    levelCount = levelCount,
    unlockLevelId = unlockLevelId,
    unlockStarsRequired = unlockStarsRequired,
    ambientMusicId = ambientMusicId,
    backgroundImageId = backgroundImageId,
)

// ----------------------------------------------------------------
// Cosmetics
// ----------------------------------------------------------------

fun SkinDto.toDomain(): Skin? = runCatching {
    Skin(
        skinId = skinId,
        displayName = displayName,
        themeName = themeName,
        boardTint = boardTint,
        particlePreset = particlePreset,
        rarity = runCatching { CosmeticRarity.valueOf(rarity) }
            .getOrDefault(CosmeticRarity.Common),
        source = runCatching { CosmeticSource.valueOf(source) }
            .getOrDefault(CosmeticSource.Default),
        unlockHint = unlockHint,
        sortOrder = sortOrder,
    )
}.getOrNull()

fun Skin.toDto(): SkinDto = SkinDto(
    skinId = skinId,
    displayName = displayName,
    themeName = themeName,
    boardTint = boardTint,
    particlePreset = particlePreset,
    rarity = rarity.name,
    source = source.name,
    unlockHint = unlockHint,
    sortOrder = sortOrder,
)

fun TitleDto.toDomain(): Title? = runCatching {
    Title(
        titleId = titleId,
        displayName = displayName,
        position = runCatching { TitlePosition.valueOf(position) }
            .getOrDefault(TitlePosition.Prefix),
        text = text,
        rarity = runCatching { CosmeticRarity.valueOf(rarity) }
            .getOrDefault(CosmeticRarity.Common),
        source = runCatching { CosmeticSource.valueOf(source) }
            .getOrDefault(CosmeticSource.Default),
        unlockHint = unlockHint,
        sortOrder = sortOrder,
    )
}.getOrNull()

fun Title.toDto(): TitleDto = TitleDto(
    titleId = titleId,
    displayName = displayName,
    position = position.name,
    text = text,
    rarity = rarity.name,
    source = source.name,
    unlockHint = unlockHint,
    sortOrder = sortOrder,
)

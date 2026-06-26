package com.blockquest.domain.usecase

import com.blockquest.domain.model.BossConfig
import com.blockquest.domain.model.LevelObjective
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.LevelType
import com.blockquest.domain.model.PieceShape
import javax.inject.Inject

class GenerateLevelUseCase @Inject constructor() {
    
    operator fun invoke(worldIndex: Int, levelNumber: Int): LevelSpec {
        val isBoss = levelNumber == 30
        val isSpecial = levelNumber == 10 || levelNumber == 20
        
        val levelId = "world_${worldIndex}_level_${levelNumber}"
        val type = when {
            isBoss -> LevelType.Boss
            isSpecial -> LevelType.Challenge
            else -> LevelType.Standard
        }
        
        val targetScore = 500 + (levelNumber * 100) + (worldIndex * 2000)
        
        // Define piece pool based on world and level type
        val pool = PieceShape.PraderaPool.toMutableList()
        if (isBoss) {
            pool.clear() // Boss levels only use boss pieces for maximum impact
            pool.add(PieceShape.Scythe)
            pool.add(PieceShape.DragonTail)
            pool.add(PieceShape.GiantHammer)
        } else if (isSpecial) {
            // Special levels have more complex shapes
            pool.add(PieceShape.Square3x3)
            pool.add(PieceShape.Cross5x5)
        }
        
        return LevelSpec(
            levelId = levelId,
            levelNumber = levelNumber,
            worldIndex = worldIndex,
            levelType = type,
            objective = if (isBoss) LevelObjective.Boss else LevelObjective.ScoreTarget,
            targetScore = targetScore,
            timeLimitSeconds = if (isSpecial || isBoss) 120f else 0f,
            boardSize = 8 to 8,
            piecePool = pool,
            isBoss = isBoss,
            bossConfig = if (isBoss) BossConfig(
                bossId = "boss_w${worldIndex}",
                bossTimeLimitSeconds = 120f,
                requiredSpecialPieceClears = 5
            ) else null
        )
    }
}

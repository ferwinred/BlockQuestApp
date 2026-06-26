package com.blockquest.domain.repository

import kotlinx.coroutines.flow.Flow

data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val score: Int
)

interface LeaderboardRepository {
    suspend fun submitScore(levelId: String, score: Int, displayName: String)
    fun observeTopScores(levelId: String, limit: Int = 10): Flow<List<LeaderboardEntry>>
}

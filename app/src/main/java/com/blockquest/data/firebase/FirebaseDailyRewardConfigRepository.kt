// =====================================================================
// FirebaseDailyRewardConfigRepository.kt
// Block Quest — DailyRewardConfigRepository implementation
// =====================================================================

package com.blockquest.data.firebase

import com.blockquest.data.firebase.dto.DailyRewardConfigDto
import com.blockquest.data.firebase.mapper.toDomain
import com.blockquest.domain.model.DailyRewardConfig
import com.blockquest.domain.repository.DailyRewardConfigRepository
import com.blockquest.domain.scoring.DailyRewardService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseDailyRewardConfigRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : DailyRewardConfigRepository {

    private val cache = MutableStateFlow(DailyRewardService.praderaConfig())

    override fun observeConfig(): Flow<DailyRewardConfig> = cache.asStateFlow()

    override suspend fun currentConfig(): DailyRewardConfig {
        // First emission is local; refresh in background.
        runCatching {
            val snap = firestore.collection("config")
                .document("dailyReward")
                .get()
                .await()
            val dto = snap.toObject(DailyRewardConfigDto::class.java) ?: return@runCatching
            val domain = dto.toDomain()
            cache.value = domain
        }
        return cache.value
    }
}

// =====================================================================
// FirebaseProgressionRepository.kt
// Block Quest — ProgressionRepository implementation
// =====================================================================

package com.blockquest.data.firebase

import com.blockquest.data.firebase.dto.LevelResultDto
import com.blockquest.data.firebase.mapper.toDomain
import com.blockquest.data.firebase.mapper.toDto
import com.blockquest.data.firebase.dto.ProgressionDto
import com.blockquest.domain.model.LevelResult
import com.blockquest.domain.model.ProgressionState
import com.blockquest.domain.repository.ProgressionRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseProgressionRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ProgressionRepository {

    private fun getDocRef(uid: String) = firestore.collection("players")
        .document(uid)
        .collection("progression")
        .document("main")

    override fun observeProgression(): Flow<ProgressionState> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(ProgressionState())
            close()
            return@callbackFlow
        }
        val registration = getDocRef(userId).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val dto = snap?.toObject(ProgressionDto::class.java) ?: ProgressionDto()
            trySend(dto.toDomain(userId))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun recordLevelResult(result: LevelResult) {
        val uid = auth.currentUser?.uid ?: return
        val current = getDocRef(uid).get().await()
            .toObject(ProgressionDto::class.java) ?: ProgressionDto()
        val existing = current.results[result.levelId]
        val merged = current.copy(
            results = current.results + (result.levelId to (
                if (existing == null) {
                    LevelResultDto(
                        completed = result.completed,
                        stars = result.stars,
                        bestScore = result.bestScore,
                        attempts = result.attempts,
                    )
                } else {
                    existing.copy(
                        completed = existing.completed || result.completed,
                        stars = maxOf(existing.stars, result.stars),
                        bestScore = maxOf(existing.bestScore, result.bestScore),
                        attempts = existing.attempts + 1,
                    )
                }
            ))
        )
        getDocRef(uid).set(merged, SetOptions.merge()).await()
    }

    override suspend fun unlockWorld(worldIndex: Int, method: String) {
        if (worldIndex !in 0..4) return
        val uid = auth.currentUser?.uid ?: return
        val current = getDocRef(uid).get().await()
            .toObject(ProgressionDto::class.java) ?: ProgressionDto()
        val list = current.worldUnlocked.toMutableList()
        if (worldIndex in list.indices) list[worldIndex] = true
        getDocRef(uid).set(current.copy(worldUnlocked = list), SetOptions.merge()).await()
    }

    override suspend fun unlockAchievement(achievementId: String) {
        val uid = auth.currentUser?.uid ?: return
        val current = getDocRef(uid).get().await()
            .toObject(ProgressionDto::class.java) ?: ProgressionDto()
        if (achievementId in current.completedAchievements) return
        val list = current.completedAchievements + achievementId
        getDocRef(uid).set(current.copy(completedAchievements = list), SetOptions.merge()).await()
    }

    override suspend fun reset() {
        val uid = auth.currentUser?.uid ?: return
        getDocRef(uid).set(ProgressionDto(), SetOptions.merge()).await()
    }
}

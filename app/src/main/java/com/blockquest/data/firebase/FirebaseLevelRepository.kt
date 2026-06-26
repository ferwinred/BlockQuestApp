// =====================================================================
// FirebaseLevelRepository.kt
// Block Quest — LevelRepository implementation (Firestore + Room cache)
// =====================================================================
//
// This is the data layer's answer to the design question in the
// original Unity spec's Sección 10.1: "do we need a backend?"
// The original answer was "no, everything local". For a real
// production release we want:
//   * Fast cold start (the level catalogue is in the local cache)
//   * Live updates (a designer can ship a balance fix without
//     shipping a new APK)
//   * Anti-cheat (server-side validation of high scores)
//
// Pattern: Room is the local mirror, Firestore is the source of
// truth. The first read comes from Room (sync, fast), then a
// background fetch from Firestore updates the cache and
// re-emits. This is the standard offline-first pattern.
//
// All Firebase calls are suspend functions; the repository
// functions as `Flow` for the presentation layer to bind to.
// =====================================================================

package com.blockquest.data.firebase

import androidx.room.withTransaction
import com.blockquest.data.firebase.dto.LevelDto
import com.blockquest.data.local.BlockQuestDatabase
import com.blockquest.data.local.LevelCacheEntity
import com.blockquest.data.local.LevelCacheDao
import com.blockquest.data.local.asDomain
import com.blockquest.data.local.asEntity
import com.blockquest.data.firebase.mapper.toDomain
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.repository.LevelRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseLevelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: BlockQuestDatabase,
    private val levelDao: LevelCacheDao,
    private val generateLevel: com.blockquest.domain.usecase.GenerateLevelUseCase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LevelRepository {

    /**
     * Stream of all known levels. We emit the local cache first
     * (synchronous, fast), then start a Firestore fetch in the
     * background. The flow re-emits when the cache changes.
     */
    override fun observeAllLevels(): Flow<List<LevelSpec>> = flow {
        // Phase 1: local cache.
        val cached = levelDao.getAll().map { it.asDomain() }
            .filter { it != null }
            .map { it!! }
        
        // If empty, generate the first 2 worlds (30 levels each)
        if (cached.isEmpty()) {
            val generated = mutableListOf<LevelSpec>()
            for (w in 0..1) {
                for (l in 1..30) {
                    generated.add(generateLevel(w, l))
                }
            }
            emit(generated)
            // Optionally persist them
            database.withTransaction {
                generated.forEach { levelDao.upsert(it.asEntity()) }
            }
        } else {
            emit(cached)
        }

        // Phase 2: refresh from Firestore (optional background sync)
        try {
            refreshFromFirestore()
            val refreshed = levelDao.getAll().map { it.asDomain() }
                .filter { it != null }
                .map { it!! }
            emit(refreshed)
        } catch (e: Exception) {
            // Log error but keep showing cached/generated levels
        }
    }.flowOn(io)

    override suspend fun getLevel(levelId: String): LevelSpec? {
        // Fast path: local cache.
        levelDao.getById(levelId)?.asDomain()?.let { return it }
        
        // If it looks like a generated ID, generate it on the fly
        if (levelId.startsWith("world_")) {
            val parts = levelId.split("_")
            if (parts.size == 4) {
                val world = parts[1].toIntOrNull()
                val num = parts[3].toIntOrNull()
                if (world != null && num != null) {
                    val gen = generateLevel(world, num)
                    database.withTransaction {
                        levelDao.upsert(gen.asEntity())
                    }
                    return gen
                }
            }
        }
        val snapshot = firestore.collection("levels")
            .document(levelId)
            .get(Source.SERVER)
            .await()
        val dto = snapshot.toObject(
            LevelDto::class.java
        ) ?: return null
        val domain = dto.toDomain() ?: return null
        database.withTransaction {
            levelDao.upsert(domain.asEntity())
        }
        return domain
    }

    private suspend fun refreshFromFirestore() {
        val snapshot = firestore.collection("levels")
            .get(Source.SERVER)
            .await()
        database.withTransaction {
            snapshot.documents.forEach { doc ->
                val dto = doc.toObject(
                    LevelDto::class.java
                ) ?: return@forEach
                dto.toDomain()?.let { levelDao.upsert(it.asEntity()) }
            }
        }
    }
}

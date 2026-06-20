// =====================================================================
// FirebaseWorldRepository.kt
// Block Quest — WorldRepository implementation
// =====================================================================

package com.blockquest.data.firebase

import com.blockquest.data.firebase.dto.WorldDto
import com.blockquest.data.firebase.mapper.toDomain
import com.blockquest.domain.model.WorldDefinition
import com.blockquest.domain.repository.WorldRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseWorldRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : WorldRepository {

    // The 5 worlds are mostly static; a small in-memory
    // cache is enough. In production this could move to
    // Room.
    private val cache = mutableListOf<WorldDefinition>()

    override fun observeWorlds(): Flow<List<WorldDefinition>> = flow {
        if (cache.isNotEmpty()) {
            emit(cache.toList())
            return@flow
        }
        val snap = firestore.collection("worlds")
            .orderBy("worldIndex")
            .get(Source.SERVER)
            .await()
        val defs = snap.documents.mapNotNull {
            it.toObject(WorldDto::class.java)?.toDomain()
        }
        synchronized(cache) {
            cache.clear()
            cache.addAll(defs)
        }
        emit(defs)
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    override suspend fun getWorld(worldIndex: Int): WorldDefinition? {
        synchronized(cache) {
            cache.firstOrNull { it.worldIndex == worldIndex }?.let { return it }
        }
        val snap = firestore.collection("worlds")
            .whereEqualTo("worldIndex", worldIndex)
            .limit(1)
            .get()
            .await()
        val def = snap.documents.firstOrNull()
            ?.toObject(WorldDto::class.java)?.toDomain()
        if (def != null) {
            synchronized(cache) { cache.add(def) }
        }
        return def
    }
}

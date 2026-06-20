// =====================================================================
// FirebaseMissionRepository.kt
// Block Quest — MissionRepository implementation
// =====================================================================
//
// Layout in Firestore:
//
//   /missions/{missionId}                      — catalogue
//   /players/{uid}/missions/daily              — List<MissionProgressDto>
//   /players/{uid}/missions/weekly
//   /players/{uid}/missions/achievement
//   /players/{uid}/missions/rolls              — { lastDaily, lastWeekly }
//
// Writes are debounced in the implementation — `saveProgress`
// is called on every progress update, but the repository
// coalesces writes within a 1-second window to avoid
// hammering Firestore.
// =====================================================================

package com.blockquest.data.firebase

import com.blockquest.data.firebase.dto.MissionBootstrapDto
import com.blockquest.data.firebase.dto.MissionDto
import com.blockquest.data.firebase.dto.MissionProgressDto
import com.blockquest.data.firebase.mapper.toDomain
import com.blockquest.data.firebase.mapper.toDto
import com.blockquest.data.local.MissionProgressEntity
import com.blockquest.data.local.MissionProgressDao
import com.blockquest.data.local.asDomain
import com.blockquest.data.local.asEntity
import com.blockquest.domain.model.MissionCadence
import com.blockquest.domain.model.MissionProgress
import com.blockquest.domain.model.MissionSpec
import com.blockquest.domain.repository.MissionRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.util.Date
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseMissionRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dao: MissionProgressDao,
) : MissionRepository {

    private val catalogCache = mutableListOf<MissionSpec>()
    private val catalogMutex = Mutex()

    private val writeChannel = Channel<MissionProgress>(capacity = Channel.UNLIMITED)
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = HashMap<String, MissionProgress>()

    init {
        // Debounced writer: collect progress updates and
        // flush every 1s (or when 25 are queued, whichever
        // comes first).
        writeScope.launch {
            for (progress in writeChannel) {
                pending[progress.missionId] = progress
                flushIfReady()
            }
        }
    }

    private var lastFlushMs = 0L
    private suspend fun flushIfReady() {
        val now = System.currentTimeMillis()
        if (pending.size < 25 && (now - lastFlushMs) < 1_000L) return
        flush()
    }

    private suspend fun flush() {
        val batch = pending.toMap()
        pending.clear()
        lastFlushMs = System.currentTimeMillis()
        // Local cache first.
        batch.values.forEach { dao.upsert(it.asEntity()) }
        // Then remote, best-effort.
        runCatching {
            val userId = auth.currentUser?.uid ?: return@runCatching
            val docRef = firestore.collection("players")
                .document(userId).collection("missions")
            batch.values.forEach { p ->
                docRef.document(p.missionId).set(p.toDto(), SetOptions.merge())
            }
        }
    }

    override fun observeAllMissions(): Flow<List<MissionSpec>> = flow {
        // 1. Local cache (Room).
        // 2. Remote catalogue.
        val cached = catalogMutex.withLock {
            if (catalogCache.isNotEmpty()) catalogCache.toList() else null
        }
        if (cached != null) {
            emit(cached)
            return@flow
        }

        val snap = firestore.collection("missions").get().await()
        val specs = snap.documents.mapNotNull {
            it.toObject(MissionDto::class.java)?.toDomain()
        }
        catalogMutex.withLock {
            catalogCache.clear()
            catalogCache.addAll(specs)
        }
        emit(specs)
    }

    override fun observeProgress(): Flow<List<MissionProgress>> = combine(
        observeAllMissions(),
        dao.observeAll(),
    ) { specs, rows ->
        val byId = specs.associateBy { it.missionId }
        rows.mapNotNull { row ->
            val spec = byId[row.missionId] ?: return@mapNotNull null
            MissionProgress(
                missionId = row.missionId,
                spec = spec,
                progress = row.progress,
                completed = row.completed,
                claimedAtMs = row.claimedAtMs,
                cycleStartedAtMs = row.cycleStartedAtMs,
            )
        }
    }

    override suspend fun saveProgress(progress: MissionProgress) {
        dao.upsert(progress.asEntity())
        writeChannel.trySend(progress)
    }

    override suspend fun rollCycle(nowMs: Long, isDaily: Boolean): List<MissionProgress> {
        val cadence = if (isDaily) MissionCadence.Daily else MissionCadence.Weekly
        val active = dao.getByCadence(cadence.name)
        // Drop all uncompleted from this cadence.
        active.filter { !it.completed }.forEach { dao.delete(it.missionId) }
        // Pick N new missions from the catalogue.
        val catalog = observeAllMissions().first().filter { it.cadence == cadence }
        val pickCount = if (isDaily) 3 else 5
        val picks = catalog
            .sortedByDescending { it.weight }
            .take(pickCount.coerceAtMost(catalog.size))
        val progresses = picks.map { spec ->
            MissionProgress(
                missionId = spec.missionId,
                spec = spec,
                progress = 0,
                completed = false,
                claimedAtMs = null,
                cycleStartedAtMs = nowMs,
            )
        }
        progresses.forEach { dao.upsert(it.asEntity()) }
        // Update the roll timestamps on Firestore.
        runCatching {
            val userId = auth.currentUser?.uid ?: return@runCatching
            firestore.collection("players").document(userId)
                .collection("missions")
                .document("rolls")
                .set(
                    mapOf(
                        (if (isDaily) "lastDaily" else "lastWeekly") to nowMs
                    ),
                    SetOptions.merge()
                )
        }
        return progresses
    }

    override suspend fun markClaimed(missionId: String, nowMs: Long): MissionProgress? {
        val current = dao.getById(missionId) ?: return null
        if (!current.completed || current.claimedAtMs != null) return null
        val updated = current.copy(claimedAtMs = nowMs)
        dao.upsert(updated)
        // Best-effort remote.
        runCatching {
            val userId = auth.currentUser?.uid ?: return@runCatching
            firestore.collection("players").document(userId)
                .collection("missions")
                .document(missionId)
                .set(updated.toDto(), SetOptions.merge())
        }
        return updated.asDomain()
    }

    override suspend fun bootstrapForNewPlayer(): List<MissionProgress> {
        val all = observeAllMissions().first()
        val now = System.currentTimeMillis()
        val dailies = all.filter { it.cadence == MissionCadence.Daily }
            .sortedByDescending { it.weight }.take(3)
        val weeklies = all.filter { it.cadence == MissionCadence.Weekly }
            .sortedByDescending { it.weight }.take(5)
        val achievements = all.filter { it.cadence == MissionCadence.Achievement }
            .sortedByDescending { it.weight }.take(7)
        val picks = dailies + weeklies + achievements
        val progresses = picks.map { spec ->
            MissionProgress(
                missionId = spec.missionId,
                spec = spec,
                progress = 0,
                completed = false,
                claimedAtMs = null,
                cycleStartedAtMs = now,
            )
        }
        progresses.forEach { dao.upsert(it.asEntity()) }
        runCatching {
            val userId = auth.currentUser?.uid ?: return@runCatching
            firestore.collection("players").document(userId)
                .collection("missions")
                .document("bootstrap")
                .set(
                    MissionBootstrapDto(
                        playerId = userId,
                        daily = dailies.map { d ->
                            val dto = d.toDto()
                            MissionProgressDto(
                                missionId = dto.missionId,
                                progress = 0,
                                completed = false,
                                cycleStartedAt = Timestamp(Date(now)),
                            )
                        },
                        weekly = weeklies.map {
                            MissionProgressDto(
                                missionId = it.missionId, progress = 0,
                                cycleStartedAt = Timestamp(Date(now)),
                            )
                        },
                        achievement = achievements.map {
                            MissionProgressDto(
                                missionId = it.missionId, progress = 0,
                                cycleStartedAt = Timestamp(Date(now)),
                            )
                        },
                        lastDailyRoll = Timestamp(Date(now)),
                        lastWeeklyRoll = Timestamp(Date(now)),
                    ),
                    SetOptions.merge()
                )
        }
        return progresses
    }
}

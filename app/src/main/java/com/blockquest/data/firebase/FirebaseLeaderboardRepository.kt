package com.blockquest.data.firebase

import com.blockquest.domain.repository.LeaderboardEntry
import com.blockquest.domain.repository.LeaderboardRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseLeaderboardRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : LeaderboardRepository {

    override suspend fun submitScore(levelId: String, score: Int, displayName: String) {
        val user = auth.currentUser ?: return
        val entry = mapOf(
            "userId" to user.uid,
            "displayName" to displayName,
            "score" to score,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        
        // Save to /leaderboard/{levelId}/scores/{userId}
        firestore.collection("leaderboard")
            .document(levelId)
            .collection("scores")
            .document(user.uid)
            .set(entry, SetOptions.merge())
    }

    override fun observeTopScores(levelId: String, limit: Int): Flow<List<LeaderboardEntry>> = callbackFlow {
        val listener = firestore.collection("leaderboard")
            .document(levelId)
            .collection("scores")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val entries = snapshot.documents.mapNotNull { doc ->
                        val userId = doc.getString("userId") ?: return@mapNotNull null
                        val displayName = doc.getString("displayName") ?: "Jugador"
                        val score = doc.getLong("score")?.toInt() ?: 0
                        LeaderboardEntry(userId, displayName, score)
                    }
                    trySend(entries)
                }
            }
            
        awaitClose { listener.remove() }
    }
}

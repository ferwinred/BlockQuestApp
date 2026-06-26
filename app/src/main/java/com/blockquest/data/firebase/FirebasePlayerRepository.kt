// =====================================================================
// FirebasePlayerRepository.kt
// Block Quest — PlayerRepository implementation (Firestore + DataStore)
// =====================================================================

package com.blockquest.data.firebase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.blockquest.data.firebase.dto.CurrencyDto
import com.blockquest.data.firebase.dto.DailyRewardDto
import com.blockquest.data.firebase.dto.PlayerDto
import com.blockquest.data.firebase.mapper.toDomain
import com.blockquest.data.firebase.mapper.toDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import com.blockquest.domain.model.CurrencyState
import com.blockquest.domain.model.DailyRewardClaimed
import com.blockquest.domain.model.DailyRewardState
import com.blockquest.domain.repository.DailyRewardConfigRepository
import com.blockquest.domain.repository.PlayerRepository
import com.blockquest.domain.scoring.DailyRewardService
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.util.Date
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebasePlayerRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val localStore: DataStore<Preferences>,
    private val dailyRewardConfigRepo: DailyRewardConfigRepository,
) : PlayerRepository {

    // Cache userId locally so we can write to Firestore even
    // before the auth state is fully resolved.
    private val keyUserId = stringPreferencesKey("user_id")
    private val keyCoins = longPreferencesKey("coins")
    private val keyGems = longPreferencesKey("gems")
    private val keyBoosters = stringPreferencesKey("boosters")
    private val keyLastDailyClaim = longPreferencesKey("daily_claim_ms")
    private val keyDailyStreak = longPreferencesKey("daily_streak")

    override fun observeCurrency(): Flow<CurrencyState> = localStore.data
        .map { prefs ->
            val boostersJson = prefs[keyBoosters] ?: "{}"
            val boostersMap = try {
                Json.decodeFromString<Map<String, Int>>(boostersJson)
            } catch (e: Exception) {
                emptyMap()
            }
            CurrencyState(
                coins = prefs[keyCoins]?.toInt() ?: 0,
                gems = prefs[keyGems]?.toInt() ?: 0,
                boosters = boostersMap
            )
        }
        .distinctUntilChanged()

    
    override suspend fun addBooster(boosterId: String, amount: Int) {
        localStore.edit { prefs ->
            val boostersJson = prefs[keyBoosters] ?: "{}"
            val map = try {
                Json.decodeFromString<Map<String, Int>>(boostersJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }
            map[boosterId] = (map[boosterId] ?: 0) + amount
            prefs[keyBoosters] = Json.encodeToString(map)
        }
        syncToFirebase()
    }
    
    override suspend fun consumeBooster(boosterId: String) {
        localStore.edit { prefs ->
            val boostersJson = prefs[keyBoosters] ?: "{}"
            val map = try {
                Json.decodeFromString<Map<String, Int>>(boostersJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }
            val current = map[boosterId] ?: 0
            if (current > 0) {
                map[boosterId] = current - 1
            }
            prefs[keyBoosters] = Json.encodeToString(map)
        }
        syncToFirebase()
    }

    override suspend fun addCoins(amount: Int, source: String) {
        if (amount <= 0) return
        localStore.edit { prefs ->
            val current = prefs[keyCoins] ?: 0L
            val new = (current + amount).coerceAtMost(999_999L)
            prefs[keyCoins] = new
        }
        // Best-effort sync to Firestore.
        runCatching { syncCurrencyToFirestore() }
    }

    override suspend fun trySpendCoins(amount: Int, destination: String): Boolean {
        if (amount <= 0) return true
        var ok = false
        localStore.edit { prefs ->
            val current = prefs[keyCoins] ?: 0L
            if (current >= amount) {
                prefs[keyCoins] = current - amount
                ok = true
            }
        }
        if (ok) runCatching { syncCurrencyToFirestore() }
        return ok
    }

    override suspend fun addGems(amount: Int, source: String) {
        if (amount <= 0) return
        localStore.edit { prefs ->
            val current = prefs[keyGems] ?: 0L
            val new = (current + amount).coerceAtMost(99_999L)
            prefs[keyGems] = new
        }
        runCatching { syncCurrencyToFirestore() }
    }

    override suspend fun trySpendGems(amount: Int, destination: String): Boolean {
        if (amount <= 0) return true
        var ok = false
        localStore.edit { prefs ->
            val current = prefs[keyGems] ?: 0L
            if (current >= amount) {
                prefs[keyGems] = current - amount
                ok = true
            }
        }
        if (ok) runCatching { syncCurrencyToFirestore() }
        return ok
    }

    override fun observeDailyReward(): Flow<DailyRewardState> = localStore.data
        .map { prefs ->
            DailyRewardState(
                lastClaimed = prefs[keyLastDailyClaim] ?: 0L,
                currentStreak = prefs[keyDailyStreak]?.toInt() ?: 0,
            )
        }
        .distinctUntilChanged()

    override suspend fun claimDailyReward(): DailyRewardClaimed? {
        // Returns the calendar day's reward (Sección 9.1 of the
        // spec). The economy config is loaded at boot and held
        // in Hilt's DI graph.
        val now = System.currentTimeMillis()
        val config = dailyRewardConfigRepo.currentConfig()
        val current = localStore.data.first()
        val state = DailyRewardState(
            lastClaimed = current[keyLastDailyClaim] ?: 0L,
            currentStreak = (current[keyDailyStreak] ?: 0L).toInt(),
        )
        if (!DailyRewardService.canClaim(state, config, now)) return null
        val claimed: DailyRewardClaimed = DailyRewardService.claim(state, config, now)
        // Persist the new state.
        localStore.edit { prefs ->
            prefs[keyLastDailyClaim] = claimed.nextAvailableAtMs
            prefs[keyDailyStreak] = claimed.newStreak.toLong()
        }
        // Award the coins / gems locally + sync to Firestore.
        addCoinsInternal(claimed.coins, "daily_reward")
        addGemsInternal(claimed.gems, "daily_reward")
        // Best-effort remote write of the daily-reward state.
        runCatching {
            val user = auth.currentUser ?: return@runCatching
            firestore.collection("players").document(user.uid)
                .set(
                    mapOf("dailyReward" to DailyRewardDto(
                        lastClaimed = Timestamp(Date(claimed.nextAvailableAtMs)),
                        currentStreak = claimed.newStreak,
                    )),
                    SetOptions.merge()
                )
        }
        return claimed
    }

    private suspend fun addCoinsInternal(amount: Int, source: String) {
        if (amount <= 0) return
        localStore.edit { prefs ->
            val current = prefs[keyCoins] ?: 0L
            prefs[keyCoins] = (current + amount).coerceAtMost(999_999L)
        }
        runCatching { syncCurrencyToFirestore() }
    }

    private suspend fun addGemsInternal(amount: Int, source: String) {
        if (amount <= 0) return
        localStore.edit { prefs ->
            val current = prefs[keyGems] ?: 0L
            prefs[keyGems] = (current + amount).coerceAtMost(99_999L)
        }
        runCatching { syncCurrencyToFirestore() }
    }

    override suspend fun ensureSignedIn(): String {
        val current = auth.currentUser
        if (current != null) return current.uid
        val result = auth.signInAnonymously().await()
        val user: FirebaseUser = result.user
            ?: error("FirebaseAuth returned a null user after signInAnonymously")
        // Seed the player document.
        firestore.collection("players")
            .document(user.uid)
            .set(
                PlayerDto(displayName = "Jugador"),
                SetOptions.merge(),
            )
        return user.uid
    }

    private suspend fun syncCurrencyToFirestore() {
        val user = auth.currentUser ?: return
        val prefs = localStore.data.first()
        val dto = CurrencyDto(
            coins = prefs[keyCoins] ?: 0L,
            gems = prefs[keyGems] ?: 0L,
        )
        firestore.collection("players")
            .document(user.uid)
            .set(mapOf("currency" to dto), SetOptions.merge())
            .await()
    }

    override suspend fun linkWithGoogle(idToken: String): Result<Unit> {
        return runCatching {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            auth.currentUser?.linkWithCredential(credential)?.await()
            val user = auth.currentUser ?: return@runCatching
            // Update displayName to match the linked Google account
            firestore.collection("players").document(user.uid)
                .set(mapOf("displayName" to (user.displayName ?: "Jugador")), SetOptions.merge())
        }
    }

    override suspend fun linkWithEmail(email: String, pass: String): Result<Unit> {
        return runCatching {
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, pass)
            auth.currentUser?.linkWithCredential(credential)?.await()
        }
    }
}

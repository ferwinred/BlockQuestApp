// =====================================================================
// FirebaseCosmeticRepository.kt
// Block Quest — CosmeticRepository implementation
// =====================================================================
//
// The catalog (all available skins and titles) is static
// for the MVP — the same for every player. In production
// this could move to Firestore so marketing can ship new
// skins without an APK update.
//
// The inventory (owned + active) is per-player and is
// stored in two places:
//   * Firestore: source of truth, used for sync across
//     devices.
//   * DataStore: local cache + active selection. The
//     active skin/title is a preference, not a document.
// =====================================================================

package com.blockquest.data.firebase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.blockquest.data.firebase.dto.SkinDto
import com.blockquest.data.firebase.dto.TitleDto
import com.blockquest.data.firebase.mapper.toDomain
import com.blockquest.domain.model.CosmeticCatalog
import com.blockquest.domain.model.CosmeticInventory
import com.blockquest.domain.model.CosmeticRarity
import com.blockquest.domain.model.CosmeticSource
import com.blockquest.domain.model.EquipResult
import com.blockquest.domain.model.Skin
import com.blockquest.domain.model.Title
import com.blockquest.domain.model.TitlePosition
import com.blockquest.domain.repository.CosmeticRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCosmeticRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val localStore: DataStore<Preferences>,
) : CosmeticRepository {

    private val keyOwnedSkins = stringSetPreferencesKey("owned_skins")
    private val keyOwnedTitles = stringSetPreferencesKey("owned_titles")
    private val keyActiveSkin = stringPreferencesKey("active_skin")
    private val keyActiveTitle = stringPreferencesKey("active_title")

    // ---- Catalog (mostly static) ----
    private val catalog: CosmeticCatalog by lazy { defaultCatalog() }

    override fun observeCatalog(): Flow<CosmeticCatalog> = kotlinx.coroutines.flow.flow {
        emit(catalog)
    }

    // ---- Inventory ----

    override fun observeInventory(): Flow<CosmeticInventory> = combine(
        localStore.data.map { prefs ->
            prefs[keyOwnedSkins] ?: setOf("default")
        },
        localStore.data.map { prefs ->
            prefs[keyOwnedTitles] ?: emptySet()
        },
        localStore.data.map { prefs ->
            prefs[keyActiveSkin] ?: "default"
        },
        localStore.data.map { prefs ->
            prefs[keyActiveTitle]
        },
    ) { skins, titles, activeSkin, activeTitle ->
        CosmeticInventory(
            ownedSkinIds = skins,
            ownedTitleIds = titles,
            activeSkinId = activeSkin,
            activeTitleId = activeTitle,
        )
    }.distinctUntilChanged()

    override suspend fun grant(cosmeticId: String) {
        val skins = catalog.skins.map { it.skinId }
        val titles = catalog.titles.map { it.titleId }
        localStore.edit { prefs ->
            when (cosmeticId) {
                in skins -> {
                    val owned = prefs[keyOwnedSkins] ?: setOf("default")
                    prefs[keyOwnedSkins] = owned + cosmeticId
                }
                in titles -> {
                    val owned = prefs[keyOwnedTitles] ?: emptySet()
                    prefs[keyOwnedTitles] = owned + cosmeticId
                }
                else -> { /* unknown id, ignore */ }
            }
        }
        syncToFirestore()
    }

    override suspend fun equipSkin(skinId: String): EquipResult {
        val owned = localStore.data.first()[keyOwnedSkins] ?: setOf("default")
        if (skinId !in owned) return EquipResult.NotOwned
        if (catalog.skins.none { it.skinId == skinId }) return EquipResult.UnknownCosmetic
        localStore.edit { prefs -> prefs[keyActiveSkin] = skinId }
        syncToFirestore()
        return EquipResult.Ok(skinId = skinId)
    }

    override suspend fun equipTitle(titleId: String?): EquipResult {
        if (titleId == null) {
            localStore.edit { prefs -> prefs.remove(keyActiveTitle) }
            syncToFirestore()
            return EquipResult.Ok(titleId = null)
        }
        val owned = localStore.data.first()[keyOwnedTitles] ?: emptySet()
        if (titleId !in owned) return EquipResult.NotOwned
        if (catalog.titles.none { it.titleId == titleId }) return EquipResult.UnknownCosmetic
        localStore.edit { prefs -> prefs[keyActiveTitle] = titleId }
        syncToFirestore()
        return EquipResult.Ok(titleId = titleId)
    }

    private suspend fun syncToFirestore() {
        val user = auth.currentUser ?: return
        val prefs = localStore.data.first()
        runCatching {
            val payload = mapOf(
                "ownedSkins" to (prefs[keyOwnedSkins] ?: setOf("default")).toList(),
                "ownedTitles" to (prefs[keyOwnedTitles] ?: emptySet()).toList(),
                "activeSkin" to (prefs[keyActiveSkin] ?: "default"),
                "activeTitle" to prefs[keyActiveTitle],
            )
            firestore.collection("players").document(user.uid)
                .collection("cosmetics")
                .document("inventory")
                .set(payload, SetOptions.merge())
                .await()
        }
    }

    // -----------------------------------------------------------------
    // Default catalog. The full catalog is in Firestore; the default
    // is what we ship with the APK so the app works offline.
    // -----------------------------------------------------------------

    private fun defaultCatalog(): CosmeticCatalog = CosmeticCatalog(
        skins = listOf(
            Skin("default", "Pradera", "pradera",
                rarity = CosmeticRarity.Common, source = CosmeticSource.Default,
                sortOrder = 0),
            Skin("bosque", "Bosque", "bosque",
                rarity = CosmeticRarity.Uncommon, source = CosmeticSource.LevelReward,
                unlockHint = "Gana 3 estrellas en 5 niveles de Pradera",
                sortOrder = 10),
            Skin("desierto", "Desierto", "desierto",
                rarity = CosmeticRarity.Rare, source = CosmeticSource.LevelReward,
                unlockHint = "Gana 3 estrellas en el nivel 30 de Pradera",
                sortOrder = 20),
            Skin("espacio", "Espacio", "espacio",
                rarity = CosmeticRarity.Epic, source = CosmeticSource.MissionReward,
                unlockHint = "Completa 20 misiones de racha",
                sortOrder = 30),
            Skin("final", "Final", "final",
                rarity = CosmeticRarity.Legendary, source = CosmeticSource.Promotional,
                unlockHint = "Disponible durante eventos especiales",
                sortOrder = 40),
            Skin("lucky", "Lucky", "lucky",
                rarity = CosmeticRarity.Mythic, source = CosmeticSource.DailyReward,
                unlockHint = "Reclama 7 recompensas diarias seguidas",
                sortOrder = 50),
            Skin("neon", "Neón", "neon",
                rarity = CosmeticRarity.Rare, source = CosmeticSource.IAP,
                unlockHint = "Paquete de bienvenida",
                sortOrder = 15),
        ),
        titles = listOf(
            Title("novata", "Novata", TitlePosition.Prefix, "🌱 Novata",
                rarity = CosmeticRarity.Common, source = CosmeticSource.Default,
                sortOrder = 0),
            Title("bloquera", "Bloquera", TitlePosition.Prefix, "🧱 Bloquera",
                rarity = CosmeticRarity.Uncommon, source = CosmeticSource.LevelReward,
                unlockHint = "Completa 10 niveles",
                sortOrder = 10),
            Title("combo_master", "Combo Master", TitlePosition.Suffix, "Combo Master",
                rarity = CosmeticRarity.Rare, source = CosmeticSource.MissionReward,
                unlockHint = "Logra un combo Ultra",
                sortOrder = 20),
            Title("racha", "En Racha", TitlePosition.Suffix, "🔥 En Racha",
                rarity = CosmeticRarity.Rare, source = CosmeticSource.MissionReward,
                unlockHint = "Logra una racha de 10",
                sortOrder = 21),
            Title("diaria", "Diaria", TitlePosition.Prefix, "📅 Diaria",
                rarity = CosmeticRarity.Uncommon, source = CosmeticSource.DailyReward,
                unlockHint = "Reclama 3 recompensas diarias",
                sortOrder = 30),
            Title("veterana", "Veterana", TitlePosition.Suffix, "⭐ Veterana",
                rarity = CosmeticRarity.Legendary, source = CosmeticSource.Achievement,
                unlockHint = "Gana 30 estrellas",
                sortOrder = 40),
            Title("mvp", "MVP", TitlePosition.Prefix, "👑 MVP",
                rarity = CosmeticRarity.Mythic, source = CosmeticSource.Promotional,
                unlockHint = "Top 1 del leaderboard",
                sortOrder = 50),
        )
    )
}

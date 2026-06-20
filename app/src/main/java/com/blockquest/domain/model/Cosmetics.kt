// =====================================================================
// Cosmetic.kt
// Block Quest — Cosmetic models: skins, titles, inventory
// =====================================================================
//
// A "cosmetic" is anything the player can own and equip that
// does not affect gameplay. We have two flavours:
//
//   * Skin:   tints the board, tray, and primary surfaces.
//             Tied to a `themeName` so the engine can swap
//             the `BlockQuestTheme` dynamically.
//   * Title:  a short prefix/suffix string that shows up
//             next to the player's name (e.g. "Sara ★Novata★").
//
// The inventory (`CosmeticInventory`) bundles everything the
// player owns. The presentation layer observes the active
// selection and the engine is free to read the active skin
// when needed (e.g. for board tinting).
// =====================================================================

package com.blockquest.domain.model

/**
 * Cosmetic rarity. Used for the cosmetics store UI, drop
 * tables, and analytics.
 */
enum class CosmeticRarity(val displayName: String) {
    Common("Common"),
    Uncommon("Uncommon"),
    Rare("Rare"),
    Epic("Epic"),
    Legendary("Legendary"),
    Mythic("Mythic"),
}

/**
 * Where a cosmetic was acquired. Used for analytics + to
 * show "How to unlock" hints in the cosmetics store.
 */
enum class CosmeticSource {
    Default,        // free, given to every new player
    LevelReward,    // drop from completing a level
    MissionReward,  // drop from a mission completion
    DailyReward,    // drop from the daily reward cycle
    IAP,            // bought with real money
    Promotional,    // marketing event
    Achievement,    // unlocked via achievement
    Debug,          // only available in debug builds
}

/**
 * A single skin. A skin bundles a `themeName` (which the
 * presentation layer uses to swap `BlockQuestTheme` palettes)
 * and a `boardTint` (an extra tint the gameplay screen can
 * apply to the cell colors).
 */
data class Skin(
    val skinId: String,
    val displayName: String,
    val themeName: String,            // matches a key in `BlockQuestTheme`
    val boardTint: Long? = null,      // ARGB packed int, optional override
    val particlePreset: String? = null,
    val rarity: CosmeticRarity = CosmeticRarity.Common,
    val source: CosmeticSource = CosmeticSource.Default,
    val unlockHint: String? = null,  // "Win 5 levels in Parader", etc.
    val sortOrder: Int = 0,
)

/**
 * A title. Has a short label that prefixes the player's
 * name on the leaderboard and in the game-over screen.
 */
data class Title(
    val titleId: String,
    val displayName: String,
    val position: TitlePosition,
    val text: String,                 // the actual prefix/suffix
    val rarity: CosmeticRarity = CosmeticRarity.Common,
    val source: CosmeticSource = CosmeticSource.Default,
    val unlockHint: String? = null,
    val sortOrder: Int = 0,
)

enum class TitlePosition { Prefix, Suffix }

/**
 * The full inventory. Held by the player; persisted in
 * DataStore (active selection) + Firestore (ownership).
 */
data class CosmeticInventory(
    val ownedSkinIds: Set<String> = setOf("default"),
    val ownedTitleIds: Set<String> = setOf(),
    val activeSkinId: String = "default",
    val activeTitleId: String? = null,
    val newlyUnlocked: List<String> = emptyList(),  // skin/title ids
) {
    val isSkinOwned: (String) -> Boolean = { it in ownedSkinIds }
    val isTitleOwned: (String) -> Boolean = { it in ownedTitleIds }
}

/**
 * A small wrapper for "all cosmetics". The cosmetics store
 * screen binds to this flow.
 */
data class CosmeticCatalog(
    val skins: List<Skin> = emptyList(),
    val titles: List<Title> = emptyList(),
)

/**
 * Result of an attempt to equip. We return a sealed result
 * because equipping a skin the player doesn't own is a bug
 * (or a sync race), not a no-op.
 */
sealed class EquipResult {
    data class Ok(val skinId: String? = null, val titleId: String? = null) : EquipResult()
    data object NotOwned : EquipResult()
    data object UnknownCosmetic : EquipResult()
}

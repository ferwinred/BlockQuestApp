// =====================================================================
// CosmeticsScreen.kt
// Block Quest — Cosmetics store (skins + titles)
// =====================================================================

package com.blockquest.presentation.ui.screen.cosmetics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as itemsGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockquest.domain.model.CosmeticRarity
import com.blockquest.domain.model.Skin
import com.blockquest.domain.model.Title
import com.blockquest.presentation.designsystem.Elevation
import com.blockquest.presentation.designsystem.Palettes
import com.blockquest.presentation.designsystem.Spacing
import com.blockquest.presentation.viewmodel.CosmeticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CosmeticsScreen(
    onBack: () -> Unit,
    viewModel: CosmeticsViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personalización") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Text(
                    text = "Skins",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                SkinGrid(
                    skins = state.skins,
                    ownedIds = state.ownedSkinIds,
                    activeId = state.activeSkinId,
                    onSkinTap = viewModel::onSkinTapped,
                )
            }
            item {
                Text(
                    text = "Títulos",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(state.titles, key = { it.titleId }) { title ->
                TitleRow(
                    title = title,
                    owned = title.titleId in state.ownedTitleIds,
                    active = title.titleId == state.activeTitleId,
                    onTap = { viewModel.onTitleTapped(title.titleId) },
                )
            }
        }
    }
}

@Composable
private fun SkinGrid(
    skins: List<Skin>,
    ownedIds: Set<String>,
    activeId: String,
    onSkinTap: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.height(((skins.size + 2) / 3 * 120).dp),
    ) {
        itemsGrid(skins, key = { it.skinId }) { skin ->
            SkinCard(
                skin = skin,
                owned = skin.skinId in ownedIds,
                active = skin.skinId == activeId,
                onTap = { onSkinTap(skin.skinId) },
            )
        }
    }
}

@Composable
private fun SkinCard(
    skin: Skin,
    owned: Boolean,
    active: Boolean,
    onTap: () -> Unit,
) {
    val palette = Palettes.forTheme(skin.themeName)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .alpha(if (owned) 1f else 0.45f)
            .clickable(enabled = owned, onClick = onTap),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (active) Elevation.sm else Elevation.none,
        ),
        colors = CardDefaults.cardColors(
            containerColor = palette.primaryContainer,
        ),
        shape = RoundedCornerShape(Spacing.md),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.sm),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(Spacing.sm))
                    .background(palette.primary),
            )
            Text(
                text = skin.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = palette.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = skin.rarity.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = palette.onPrimaryContainer,
            )
            if (active) {
                Text(
                    text = "✓ Activo",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primary,
                    fontWeight = FontWeight.Bold,
                )
            } else if (!owned) {
                Text(
                    text = skin.unlockHint ?: "Bloqueado",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun TitleRow(
    title: Title,
    owned: Boolean,
    active: Boolean,
    onTap: () -> Unit,
) {
    val background = when {
        active -> MaterialTheme.colorScheme.primary
        owned -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        active -> MaterialTheme.colorScheme.onPrimary
        owned -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (owned) 1f else 0.5f)
            .clickable(enabled = owned, onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = title.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = foreground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${title.position.name} · ${title.rarity.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground,
                )
                if (!owned && title.unlockHint != null) {
                    Text(
                        text = title.unlockHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = foreground,
                    )
                }
            }
            if (active) {
                Text("✓", style = MaterialTheme.typography.titleLarge, color = foreground)
            } else if (owned) {
                Text("Equipar", color = foreground, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

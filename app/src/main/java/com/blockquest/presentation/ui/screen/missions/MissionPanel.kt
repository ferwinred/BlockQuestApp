// =====================================================================
// MissionPanel.kt
// Block Quest — Missions / Quests panel (Compose)
// =====================================================================

package com.blockquest.presentation.ui.screen.missions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockquest.domain.model.MissionCadence
import com.blockquest.domain.model.MissionProgress
import com.blockquest.presentation.viewmodel.MissionViewModel

@Composable
fun MissionPanel(viewModel: MissionViewModel = hiltViewModel()) {
    val missions by viewModel.progress.collectAsStateWithLifecycle()
    val dailies = missions.filter { it.spec.cadence == MissionCadence.Daily }
    val weeklies = missions.filter { it.spec.cadence == MissionCadence.Weekly }
    val achievements = missions.filter { it.spec.cadence == MissionCadence.Achievement }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("Diarias") }
        items(dailies, key = { it.missionId }) { m ->
            MissionRow(mission = m, onClaim = { viewModel.claimReward(m.missionId) })
        }
        item { SectionHeader("Semanales") }
        items(weeklies, key = { it.missionId }) { m ->
            MissionRow(mission = m, onClaim = { viewModel.claimReward(m.missionId) })
        }
        item { SectionHeader("Logros") }
        items(achievements, key = { it.missionId }) { m ->
            MissionRow(mission = m, onClaim = { viewModel.claimReward(m.missionId) })
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun MissionRow(
    mission: MissionProgress,
    onClaim: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = mission.spec.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${mission.progress}/${mission.spec.target}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { mission.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "🏆 +${mission.spec.rewardCoins}  💎 +${mission.spec.rewardGems}",
                    style = MaterialTheme.typography.bodySmall,
                )
                when {
                    mission.isClaimed -> Text(
                        "✅ Reclamado",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    mission.completed -> Button(onClick = onClaim) {
                        Text("Reclamar")
                    }
                    else -> Text(
                        text = "En progreso",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

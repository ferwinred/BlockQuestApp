// =====================================================================
// GameOverDialog.kt
// Block Quest — "Game over" overlay with continue options
// =====================================================================

package com.blockquest.presentation.ui.screen.gameplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blockquest.presentation.viewmodel.GameplayOverlay

@Composable
fun GameOverDialog(
    overlay: GameplayOverlay.GameOver,
    isAdInProgress: Boolean,
    onWatchAd: () -> Unit,
    onSpendGems: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val reasonText = when (overlay.reason) {
        "no_space" -> "Sin espacio para colocar una pieza"
        "timeout" -> "Se acabó el tiempo"
        "no_pieces" -> "No quedan piezas"
        else -> "Partida finalizada"
    }
    AlertDialog(
        onDismissRequest = { /* forced choice */ },
        title = {
            Text(
                text = "Game Over",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Column {
                Text(reasonText, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Puntaje: ${overlay.finalScore}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                if (overlay.canContinue) {
                    Text(
                        text = "💡 Puedes continuar 1 vez por nivel:",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("• ▶️ Mira un anuncio → 3 piezas extra", style = MaterialTheme.typography.bodySmall)
                    Text("• 💎 Gasta 50 gemas → revivir y 5 jugadas seguras", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        text = "Ya usaste tu continuación. ¡Inténtalo de nuevo!",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (isAdInProgress) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text("Reproduciendo anuncio…")
                    }
                }
            }
        },
        confirmButton = {
            if (overlay.canContinue) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onWatchAd,
                        enabled = !isAdInProgress,
                    ) {
                        Text(if (isAdInProgress) "Cargando…" else "▶️ Ver anuncio")
                    }
                    OutlinedButton(
                        onClick = onSpendGems,
                        enabled = !isAdInProgress,
                    ) {
                        Text("💎 Gastar 50 gemas")
                    }
                }
            } else {
                Button(onClick = onRetry) {
                    Text("Reintentar")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onExit) { Text("Salir") }
                if (overlay.canContinue) {
                    OutlinedButton(onClick = onRetry) { Text("Reintentar") }
                }
            }
        },
    )
}

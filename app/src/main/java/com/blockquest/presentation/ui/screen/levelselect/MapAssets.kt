package com.blockquest.presentation.ui.screen.levelselect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MapAssets(worldIndex: Int, mapWidth: Dp, mapHeight: Dp) {
    // Aquí puedes reemplazar estos Box/Text con Image(painterResource(id = R.drawable.tu_imagen))
    
    when (worldIndex) {
        0 -> { // Pradera
            MapDecoration(x = mapWidth * 0.2f, y = mapHeight * 0.8f, label = "🌳") // Árbol
            MapDecoration(x = mapWidth * 0.7f, y = mapHeight * 0.6f, label = "🦋") // Mariposa
            MapDecoration(x = mapWidth * 0.3f, y = mapHeight * 0.4f, label = "🌉") // Puente
            MapDecoration(x = mapWidth * 0.8f, y = mapHeight * 0.2f, label = "🏵️") // Árbol de Oro (Boss)
        }
        1 -> { // Desierto
            MapDecoration(x = mapWidth * 0.2f, y = mapHeight * 0.8f, label = "🌵") // Cactus
            MapDecoration(x = mapWidth * 0.7f, y = mapHeight * 0.5f, label = "🐫") // Camello
            MapDecoration(x = mapWidth * 0.4f, y = mapHeight * 0.3f, label = "☀️") // Sol
            MapDecoration(x = mapWidth * 0.6f, y = mapHeight * 0.1f, label = "🏜️") // Oasis (Boss)
        }
        2 -> { // Bosque
            MapDecoration(x = mapWidth * 0.8f, y = mapHeight * 0.8f, label = "🍄") // Hongo
            MapDecoration(x = mapWidth * 0.2f, y = mapHeight * 0.6f, label = "🐿️") // Ardilla
            MapDecoration(x = mapWidth * 0.7f, y = mapHeight * 0.4f, label = "🐦") // Pájaros
            MapDecoration(x = mapWidth * 0.5f, y = mapHeight * 0.1f, label = "🦉") // Búho gigante (Boss)
        }
    }
}

@Composable
private fun MapDecoration(x: Dp, y: Dp, label: String) {
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(64.dp)
            .background(Color.White.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, modifier = Modifier.size(32.dp))
    }
}

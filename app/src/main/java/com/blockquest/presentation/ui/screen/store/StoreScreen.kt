package com.blockquest.presentation.ui.screen.store

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// Definición de colores basados en el diseño de Figma
val StoreBgColor = Color(0xFFF2F6ED)
val TextDarkGreen = Color(0xFF193621)
val ButtonGreen = Color(0xFF6BC573)
val TextLightGrey = Color(0xFF879F8B)
val HighlightYellow = Color(0xFFFFBC20)

@Composable
fun StoreScreen(
    onBack: () -> Unit = {},
    onPurchaseGems: (Int) -> Unit = {},
    coinBalance: Int = 1250
) {
    var selectedTab by remember { mutableStateOf("Packs") }
    val tabs = listOf("Monedas", "Boosters", "Packs", "Mundos")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StoreBgColor)
            .padding(top = 32.dp, start = 16.dp, end = 16.dp)
    ) {
        // Cabecera: Título y Monedas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🛒 Tienda",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDarkGreen
            )

            // Pill de monedas
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text("🪙", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%,d".format(coinBalance),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE4DAF2) // Color lila claro del screenshot
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) ButtonGreen else Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = tab }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = tab,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextLightGrey
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Grid de Productos
        val storeItems = listOf(
            StoreItem("Pack Bienvenida", "500 \uD83E\uDE99 + 3 boosters + sin ads 24h", "$0.99", "📦", isBestValue = true),
            StoreItem("Pack Popular", "2k \uD83E\uDE99 + 10 boosters + 50 💎", "$2.99", "📦"),
            StoreItem("Pack Grande", "5k \uD83E\uDE99 + 25 boosters + 150 💎", "$4.99", "📦"),
            StoreItem("Sin Anuncios", "Quita interstitials permanentemente", "$2.99", "🚫"),
            StoreItem("Mundo Desierto", "Desbloquea el mundo 4", "$0.99", "🏜"),
            StoreItem("Mundo Neón", "Desbloquea el mundo 5", "$1.99", "🌃")
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(storeItems) { item ->
                StoreItemCard(item = item) {
                    onPurchaseGems(100) // Mock action
                }
            }
        }
    }
}

data class StoreItem(
    val title: String,
    val description: String,
    val price: String,
    val icon: String,
    val isBestValue: Boolean = false
)

@Composable
fun StoreItemCard(item: StoreItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Badge "Mejor valor"
        if (item.isBestValue) {
            Surface(
                color = HighlightYellow,
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp)
                    .zIndex(1f)
            ) {
                Text(
                    text = "★ Mejor valor",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = if (item.isBestValue) BorderStroke(1.dp, HighlightYellow) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (item.isBestValue) 6.dp else 0.dp) // Espacio para el badge si aplica
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(12.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Icono (emoji en este caso)
                Text(text = item.icon, fontSize = 32.sp)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Título
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDarkGreen,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Descripción
                Text(
                    text = item.description,
                    fontSize = 10.sp,
                    color = TextLightGrey,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp,
                    modifier = Modifier.height(28.dp) // Altura fija para alinear botones
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Botón de compra
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonGreen),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = item.price,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

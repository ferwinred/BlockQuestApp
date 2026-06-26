import re

with open('app/src/main/java/com/blockquest/presentation/ui/screen/levelselect/LevelSelectScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add states inside LevelPreviewSheet
level_preview_start = '        Box(contentAlignment = Alignment.BottomCenter) {'
if 'var selectedBoosters by remember { mutableStateOf(emptyList<String>()) }' not in content:
    new_state = '''        var selectedBoosters by remember { mutableStateOf(emptyList<String>()) }
        var boosterSelectionManual by remember { mutableStateOf(false) }
        
        Box(contentAlignment = Alignment.BottomCenter) {'''
    content = content.replace(level_preview_start, new_state)

# Replace the Button and Leaderboard section
old_button_section = '''                        Button(
                            onClick = { onPlay(lvl.levelId) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("¡JUGAR!")
                        }'''

if old_button_section in content:
    new_button_section = '''
                        // Booster Selection
                        Text("Equipar Boosters (Máx 3)", style = MaterialTheme.typography.titleSmall)
                        val availableBoosters = inventory.filterValues { it > 0 }
                        if (availableBoosters.isEmpty()) {
                            Text("No tienes boosters", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableBoosters.forEach { (boosterId, count) ->
                                    val isSelected = selectedBoosters.contains(boosterId)
                                    val name = when(boosterId) {
                                        "booster_bomb" -> "Bomba"
                                        "booster_reroll" -> "Mano"
                                        "booster_smart_move" -> "Auto"
                                        "booster_double_score" -> "Puntosx2"
                                        "booster_time_freeze" -> "Reloj"
                                        else -> boosterId
                                    }
                                    Surface(
                                        color = if (isSelected) Color(0xFF4CAF50) else Color.LightGray,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable {
                                            boosterSelectionManual = true
                                            if (isSelected) {
                                                selectedBoosters = selectedBoosters - boosterId
                                            } else if (selectedBoosters.size < 3) {
                                                selectedBoosters = selectedBoosters + boosterId
                                            }
                                        }
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(name, color = if(isSelected) Color.White else Color.Black, fontSize = 12.sp)
                                            Text("x$count", color = if(isSelected) Color.White else Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val finalBoosters = if (boosterSelectionManual) {
                                    selectedBoosters
                                } else {
                                    inventory.filterValues { it > 0 }.keys.shuffled().take(3)
                                }
                                onPlay(lvl.levelId, finalBoosters)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("¡JUGAR!")
                        }'''
    content = content.replace(old_button_section, new_button_section)

# We need to make sure to add `import androidx.compose.ui.unit.sp`
if 'import androidx.compose.ui.unit.sp' not in content:
    content = content.replace('import androidx.compose.ui.unit.dp', 'import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp')
if 'import androidx.compose.foundation.horizontalScroll' not in content:
    content = content.replace('import androidx.compose.foundation.verticalScroll', 'import androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.horizontalScroll')
if 'import androidx.compose.runtime.mutableStateOf' not in content:
    content = content.replace('import androidx.compose.runtime.mutableIntStateOf', 'import androidx.compose.runtime.mutableIntStateOf\nimport androidx.compose.runtime.mutableStateOf')
    
with open('app/src/main/java/com/blockquest/presentation/ui/screen/levelselect/LevelSelectScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

import re

with open('app/src/main/java/com/blockquest/presentation/ui/screen/gameplay/GameplayScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add currency state
if 'val currency by viewModel.currency.collectAsStateWithLifecycle' not in content:
    state_str = 'val ui by viewModel.ui.collectAsStateWithLifecycle()'
    new_state = 'val ui by viewModel.ui.collectAsStateWithLifecycle()\n    val currency by viewModel.currency.collectAsStateWithLifecycle(initialValue = com.blockquest.domain.model.CurrencyState())'
    content = content.replace(state_str, new_state)

# Inject BoosterToolbar UI
if 'BoosterToolbar' not in content:
    tray_str = '            TrayRow('
    toolbar_ui = '''            if (initialBoosters.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    initialBoosters.forEach { boosterId ->
                        val count = currency.boosters[boosterId] ?: 0
                        val name = when(boosterId) {
                            "booster_bomb" -> "Bomba"
                            "booster_reroll" -> "Mano"
                            "booster_smart_move" -> "Auto"
                            "booster_double_score" -> "x2"
                            "booster_time_freeze" -> "Reloj"
                            else -> boosterId
                        }
                        Button(
                            onClick = { if (count > 0) viewModel.useBooster(boosterId) },
                            enabled = count > 0,
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("${name} (${count})", fontSize = 12.sp)
                        }
                    }
                }
            }
            
            TrayRow('''
    content = content.replace(tray_str, toolbar_ui)

with open('app/src/main/java/com/blockquest/presentation/ui/screen/gameplay/GameplayScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

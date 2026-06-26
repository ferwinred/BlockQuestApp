import re

with open('app/src/main/java/com/blockquest/presentation/ui/screen/gameplay/GameplayScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Need to import LevelType
if "import com.blockquest.domain.model.LevelType" not in content:
    content = content.replace('import com.blockquest.domain.model.PieceShape', 'import com.blockquest.domain.model.PieceShape\nimport com.blockquest.domain.model.LevelType')

# Inject state variable
state_var_str = "    var scoreTrigger    by remember { mutableStateOf<Any?>(null) }"
new_state_var = state_var_str + "\n    var showTutorial by remember { mutableStateOf(true) }"
content = content.replace(state_var_str, new_state_var)

# Inject the Dialog in the Scaffold's Box
box_end_str = "            ComboParticleOverlay(burstKey = comboBurstKey)"
dialog_ui = """            if (showTutorial && level?.levelType?.name == "Tutorial") {
                AlertDialog(
                    onDismissRequest = { showTutorial = false },
                    title = { Text("¡Nivel Tutorial!") },
                    text = { Text("Aprende nuevas mecánicas. Arrastra las piezas al tablero. Algunas celdas o piezas pueden tener comportamientos especiales. ¡Descúbrelas!") },
                    confirmButton = {
                        Button(onClick = { showTutorial = false }) {
                            Text("¡Entendido!")
                        }
                    }
                )
            }
            
            ComboParticleOverlay(burstKey = comboBurstKey)"""
content = content.replace(box_end_str, dialog_ui)

with open('app/src/main/java/com/blockquest/presentation/ui/screen/gameplay/GameplayScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

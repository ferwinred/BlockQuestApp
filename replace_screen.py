import re

with open('app/src/main/java/com/blockquest/presentation/ui/screen/levelselect/LevelSelectScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

start_str = '                    WorldMapPath('
end_str = '                    )'
start_idx = content.find(start_str)
end_idx = content.find(end_str, start_idx) + len(end_str)

new_call = """                    InteractiveWorldMap(
                        worldIndex = state.worldIndex,
                        levels = state.levels,
                        onLevelClick = { item ->
                            if (!item.isUnlocked) {
                                scope.launch {
                                    snackbar.showSnackbar("🔒 Completa los niveles anteriores")
                                }
                            } else {
                                previewLevel = item.spec
                            }
                        }
                    )"""

content = content[:start_idx] + new_call + content[end_idx:]

# Remove WorldMapPath completely
func_start = '@Composable\nprivate fun WorldMapPath('
func_start_idx = content.find(func_start)
if func_start_idx != -1:
    # Find the end of this composable (it's right before @Composable private fun LevelNode)
    next_func_start = '@Composable\nprivate fun LevelNode('
    next_func_idx = content.find(next_func_start, func_start_idx)
    content = content[:func_start_idx] + content[next_func_idx:]

with open('app/src/main/java/com/blockquest/presentation/ui/screen/levelselect/LevelSelectScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

import re

with open('app/src/main/java/com/blockquest/navigation/NavGraph.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Update Routes
if 'GAMEPLAY     = "gameplay/{levelId}?boosters={boosters}"' not in content:
    content = content.replace('const val GAMEPLAY     = "gameplay/{levelId}"', 'const val GAMEPLAY     = "gameplay/{levelId}?boosters={boosters}"')
    content = content.replace('fun gameplay(levelId: String)    = "gameplay/$levelId"', 'fun gameplay(levelId: String, boosters: List<String> = emptyList()) = "gameplay/$levelId?boosters=${boosters.joinToString(",")}"')

# Update composable(Routes.LEVEL_SELECT)
level_select_pattern = r'onLevelSelected = \{ levelId ->\s*nav.navigate\(Routes.gameplay\(levelId\)\)\s*\},'
if 'onLevelSelected = { levelId, boosters ->' not in content:
    content = re.sub(level_select_pattern, r'onLevelSelected = { levelId, boosters ->\n                    nav.navigate(Routes.gameplay(levelId, boosters))\n                },', content)
    # Also update onLevelSelected parameter in NavGraph.kt just in case it doesn't match exactly
    content = content.replace('onLevelSelected = { levelId ->\n                    nav.navigate(Routes.gameplay(levelId))\n                },', 'onLevelSelected = { levelId, boosters ->\n                    nav.navigate(Routes.gameplay(levelId, boosters))\n                },')

# Update composable(Routes.GAMEPLAY)
gameplay_pattern = r'composable\(Routes.GAMEPLAY\) \{ entry ->\s*val levelId = entry.arguments\?\.getString\("levelId"\) \?: return@composable\s*GameplayScreen\(\s*levelId = levelId,\s*onExit  = \{ nav.popBackStack\(\) \},\s*\)\s*\}'

new_gameplay = '''composable(Routes.GAMEPLAY) { entry ->
            val levelId = entry.arguments?.getString("levelId") ?: return@composable
            val boostersStr = entry.arguments?.getString("boosters")
            val boosters = if (boostersStr.isNullOrEmpty()) emptyList() else boostersStr.split(",")
            GameplayScreen(
                levelId = levelId,
                initialBoosters = boosters,
                onExit  = { nav.popBackStack() },
            )
        }'''

if 'initialBoosters' not in content:
    content = re.sub(gameplay_pattern, new_gameplay, content)

with open('app/src/main/java/com/blockquest/navigation/NavGraph.kt', 'w', encoding='utf-8') as f:
    f.write(content)

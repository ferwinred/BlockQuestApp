import re

with open('app/src/main/java/com/blockquest/data/firebase/FirestoreSeeder.kt', 'r', encoding='utf-8') as f:
    content = f.read()

start_str = 'private suspend fun seedLevels(firestore: FirebaseFirestore) {'
end_str = '    private suspend fun seedMissions(firestore: FirebaseFirestore) {'

start_idx = content.find(start_str)
end_idx = content.find(end_str)

new_seed = """    private suspend fun seedLevels(firestore: FirebaseFirestore) {
        val levels = buildList {
            // Mundo 0 - Pradera (1-30)
            for (i in 1..30) {
                val isTutorial = i <= 4
                val isSpecial = i == 10 || i == 20
                val isBoss = i == 30
                add(level(
                    id = "world0_level$i", number = i, world = 0,
                    targetScore = if (isTutorial) 300 * i else 1000 * i,
                    timeLimit = if (isSpecial || isBoss) (if (isBoss) 120.0 else 60.0) else 0.0,
                    piecePool = if (isBoss) listOf("line_h1x5", "square_3x3", "cross_5x5") else listOf("line_h1x2", "square_2x2"),
                    rewardCoins = if (isBoss) 200 else if (isSpecial) 100 else 50,
                    rewardGems = if (isBoss) 5 else if (isSpecial) 2 else 0,
                    difficulty = 1.0 + (i * 0.1),
                    isMilestone = isSpecial || isBoss,
                    isBoss = isBoss,
                    levelType = if (isTutorial) "Tutorial" else if (isSpecial) "Challenge" else if (isBoss) "Boss" else "Standard"
                ))
            }
            // Mundo 1 - Bosque (31-60)
            for (i in 31..60) {
                val isTutorial = i <= 34
                val isSpecial = i == 40 || i == 50
                val isBoss = i == 60
                add(level(
                    id = "world1_level$i", number = i, world = 1,
                    targetScore = if (isTutorial) 500 * (i-30) else 1500 * (i-30),
                    timeLimit = if (isSpecial || isBoss) (if (isBoss) 150.0 else 90.0) else 0.0,
                    piecePool = if (isBoss) listOf("line_h1x5", "square_3x3", "cross_5x5", "u_shape") else listOf("line_h1x3", "square_2x2", "t_block"),
                    rewardCoins = if (isBoss) 300 else if (isSpecial) 150 else 75,
                    rewardGems = if (isBoss) 10 else if (isSpecial) 3 else 0,
                    difficulty = 2.0 + ((i-30) * 0.15),
                    isMilestone = isSpecial || isBoss,
                    isBoss = isBoss,
                    levelType = if (isTutorial) "Tutorial" else if (isSpecial) "Challenge" else if (isBoss) "Boss" else "Standard"
                ))
            }
            // Mundo 2 - Desierto (61-90)
            for (i in 61..90) {
                val isTutorial = i <= 64
                val isSpecial = i == 70 || i == 80
                val isBoss = i == 90
                add(level(
                    id = "world2_level$i", number = i, world = 2,
                    targetScore = if (isTutorial) 800 * (i-60) else 2000 * (i-60),
                    timeLimit = if (isSpecial || isBoss) (if (isBoss) 180.0 else 100.0) else 0.0,
                    piecePool = if (isBoss) listOf("line_h1x5", "square_3x3", "cross_5x5", "scythe", "z_block") else listOf("line_h1x4", "square_2x2", "l_corner_s"),
                    rewardCoins = if (isBoss) 400 else if (isSpecial) 200 else 100,
                    rewardGems = if (isBoss) 15 else if (isSpecial) 5 else 0,
                    difficulty = 3.0 + ((i-60) * 0.2),
                    isMilestone = isSpecial || isBoss,
                    isBoss = isBoss,
                    levelType = if (isTutorial) "Tutorial" else if (isSpecial) "Challenge" else if (isBoss) "Boss" else "Standard"
                ))
            }
        }
        
        levels.forEach { level ->
            firestore.collection("levels")
                .document(level["levelId"] as String)
                .set(level, SetOptions.merge())
                .await()
        }
        Timber.d("FirestoreSeeder: levels seeded (${levels.size})")
    }

"""

with open('app/src/main/java/com/blockquest/data/firebase/FirestoreSeeder.kt', 'w', encoding='utf-8') as f:
    f.write(content[:start_idx] + new_seed + content[end_idx:])

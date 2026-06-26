import re

with open('app/src/main/java/com/blockquest/data/firebase/FirestoreSeeder.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace "levelCount" to 10 with "levelCount" to 30
content = content.replace('"levelCount" to 10', '"levelCount" to 30')
# Unlock stars required for world 1 from 15 to 45 (15 levels * 3 stars)
content = content.replace('"unlockStarsRequired" to 15', '"unlockStarsRequired" to 45')
# Unlock stars required for world 2 from 45 to 90 (30 levels * 3 stars)
content = content.replace('"unlockStarsRequired" to 45', '"unlockStarsRequired" to 90')
# Unlock level id for world 1 from world0_level10 to world0_level30
content = content.replace('"unlockLevelId" to "world0_level10"', '"unlockLevelId" to "world0_level30"')
# Unlock level id for world 2 from world1_level10 to world1_level60
content = content.replace('"unlockLevelId" to "world1_level10"', '"unlockLevelId" to "world1_level60"')

with open('app/src/main/java/com/blockquest/data/firebase/FirestoreSeeder.kt', 'w', encoding='utf-8') as f:
    f.write(content)

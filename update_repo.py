import re

with open('app/src/main/java/com/blockquest/data/firebase/FirebasePlayerRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add import for Json
if 'import kotlinx.serialization.json.Json' not in content:
    content = content.replace('import com.blockquest.domain.model.CurrencyState', 'import kotlinx.serialization.json.Json\nimport kotlinx.serialization.decodeFromString\nimport kotlinx.serialization.encodeToString\nimport com.blockquest.domain.model.CurrencyState')

# Add key for boosters
if 'val keyBoosters =' not in content:
    content = content.replace('private val keyGems = longPreferencesKey("gems")', 'private val keyGems = longPreferencesKey("gems")\n    private val keyBoosters = stringPreferencesKey("boosters")')
    # Add stringPreferencesKey import
    content = content.replace('import androidx.datastore.preferences.core.longPreferencesKey', 'import androidx.datastore.preferences.core.longPreferencesKey\nimport androidx.datastore.preferences.core.stringPreferencesKey')

# Modify observeCurrency
observe_pattern = r'override fun observeCurrency\(\): Flow<CurrencyState> = localStore\.data\s*\.map { prefs ->\s*CurrencyState\(\s*coins = prefs\[keyCoins\]\?\.toInt\(\) \?: 0,\s*gems = prefs\[keyGems\]\?\.toInt\(\) \?: 0,\s*\)\s*}'
def replace_observe(match):
    return '''override fun observeCurrency(): Flow<CurrencyState> = localStore.data
        .map { prefs ->
            val boostersJson = prefs[keyBoosters] ?: "{}"
            val boostersMap = try {
                Json.decodeFromString<Map<String, Int>>(boostersJson)
            } catch (e: Exception) {
                emptyMap()
            }
            CurrencyState(
                coins = prefs[keyCoins]?.toInt() ?: 0,
                gems = prefs[keyGems]?.toInt() ?: 0,
                boosters = boostersMap
            )
        }'''
content = re.sub(observe_pattern, replace_observe, content)

# Modify _currencyState updating functions
# Add addBooster and consumeBooster
add_booster_func = '''
    override suspend fun addBooster(boosterId: String, amount: Int) {
        localStore.edit { prefs ->
            val boostersJson = prefs[keyBoosters] ?: "{}"
            val map = try {
                Json.decodeFromString<Map<String, Int>>(boostersJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }
            map[boosterId] = (map[boosterId] ?: 0) + amount
            prefs[keyBoosters] = Json.encodeToString(map)
        }
        syncToFirebase()
    }
    
    override suspend fun consumeBooster(boosterId: String) {
        localStore.edit { prefs ->
            val boostersJson = prefs[keyBoosters] ?: "{}"
            val map = try {
                Json.decodeFromString<Map<String, Int>>(boostersJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }
            val current = map[boosterId] ?: 0
            if (current > 0) {
                map[boosterId] = current - 1
            }
            prefs[keyBoosters] = Json.encodeToString(map)
        }
        syncToFirebase()
    }
'''
if 'override suspend fun addBooster' not in content:
    content = content.replace('override suspend fun addCoins', add_booster_func + '\n    override suspend fun addCoins')

# syncToFirebase mapper update might also be needed? 
# The Mapper for PlayerDto maps it properly? 
# Actually Mapper.kt needs to be updated to include boosters mapping.

with open('app/src/main/java/com/blockquest/data/firebase/FirebasePlayerRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)

import re

# Update Dtos.kt
with open('app/src/main/java/com/blockquest/data/firebase/dto/Dtos.kt', 'r', encoding='utf-8') as f:
    dtos_content = f.read()

# Add boosters to CurrencyDto
currency_dto_pattern = r'data class CurrencyDto\([\s\S]*?\)'
def replace_currency_dto(match):
    text = match.group(0)
    if 'val boosters: Map<String, Int>' not in text:
        text = text.replace('val gems: Long = 0,', 'val gems: Long = 0,\n    val boosters: Map<String, Int> = emptyMap(),')
    return text

dtos_content = re.sub(currency_dto_pattern, replace_currency_dto, dtos_content)

with open('app/src/main/java/com/blockquest/data/firebase/dto/Dtos.kt', 'w', encoding='utf-8') as f:
    f.write(dtos_content)


# Update Models.kt
with open('app/src/main/java/com/blockquest/domain/model/Models.kt', 'r', encoding='utf-8') as f:
    models_content = f.read()

# Add boosters to CurrencyState
currency_state_pattern = r'data class CurrencyState\([\s\S]*?\)'
def replace_currency_state(match):
    text = match.group(0)
    if 'val boosters: Map<String, Int>' not in text:
        text = text.replace('val gems: Int = 0,', 'val gems: Int = 0,\n    val boosters: Map<String, Int> = emptyMap(),')
    return text

models_content = re.sub(currency_state_pattern, replace_currency_state, models_content)

with open('app/src/main/java/com/blockquest/domain/model/Models.kt', 'w', encoding='utf-8') as f:
    f.write(models_content)

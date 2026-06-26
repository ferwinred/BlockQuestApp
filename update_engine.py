import re

with open('app/src/main/java/com/blockquest/domain/usecase/GameplayEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update GameState
state_pattern = r'val rngSeed: Int = 0,'
new_state = 'val rngSeed: Int = 0,\n    val doubleScoreRemainingMs: Long = 0L,\n    val timeFreezeRemainingMs: Long = 0L,'
if 'val doubleScoreRemainingMs: Long = 0L' not in content:
    content = content.replace(state_pattern, new_state)

# 2. Update GameEvent
event_pattern = r'data class PowerUpUsed\(val powerUpId: String, val levelId: String\) : GameEvent\(\)'
if 'data class BoosterUsed' not in content:
    content = re.sub(event_pattern, 'data class PowerUpUsed(val powerUpId: String, val levelId: String) : GameEvent()\n    data class BoosterUsed(val boosterId: String) : GameEvent()', content)

# 3. Update tick logic
tick_pattern = r'val newTime = \(s\.timeRemainingMs - dtMs\)\.coerceAtLeast\(0L?\)'
new_tick = '''var newTime = s.timeRemainingMs
            var newFreeze = (s.timeFreezeRemainingMs - dtMs).coerceAtLeast(0)
            if (s.timeFreezeRemainingMs <= 0) {
                newTime = (s.timeRemainingMs - dtMs).coerceAtLeast(0)
            }
            val newDouble = (s.doubleScoreRemainingMs - dtMs).coerceAtLeast(0)'''
if 'timeFreezeRemainingMs <=' not in content:
    content = re.sub(tick_pattern, new_tick, content)
    # also replace the state copy inside tick
    state_copy_pattern = r'timeRemainingMs = newTime'
    new_state_copy = 'timeRemainingMs = newTime,\n                timeFreezeRemainingMs = newFreeze,\n                doubleScoreRemainingMs = newDouble'
    content = content.replace(state_copy_pattern, new_state_copy)

# 4. Inject useBooster
booster_func = '''
    fun useBooster(boosterId: String) {
        val s = _state.value
        if (s.level == null) return
        
        when (boosterId) {
            "booster_bomb" -> {
                // Clear a random 3x3 area
                val (w, h) = s.level.boardSize
                val centerCol = seedRandom.nextInt(w - 2) + 1
                val centerRow = seedRandom.nextInt(h - 2) + 1
                for (c in centerCol - 1 .. centerCol + 1) {
                    for (r in centerRow - 1 .. centerRow + 1) {
                        s.board.set(c, r, CellState.Empty)
                    }
                }
            }
            "booster_reroll" -> {
                pieceSelector?.let {
                    val newTray = it.buildTray(3, s.level)
                    _state.value = s.copy(tray = newTray)
                    _events.tryEmit(GameEvent.TrayRefreshed(newTray))
                }
            }
            "booster_smart_move" -> {
                // Find first valid piece and position
                val (w, h) = s.level.boardSize
                for ((index, piece) in s.tray.withIndex()) {
                    for (c in 0 until w) {
                        for (r in 0 until h) {
                            if (BoardValidator.canPlace(s.board, piece, c, r)) {
                                placePiece(index, c, r)
                                return // Place only one piece
                            }
                        }
                    }
                }
            }
            "booster_double_score" -> {
                _state.value = s.copy(doubleScoreRemainingMs = 15000L) // 15 seconds
            }
            "booster_time_freeze" -> {
                _state.value = s.copy(timeFreezeRemainingMs = 15000L) // 15 seconds
            }
        }
        _events.tryEmit(GameEvent.BoosterUsed(boosterId))
    }
'''
if 'fun useBooster' not in content:
    content = content.replace('fun tick(dtMs: Long)', booster_func + '\n    fun tick(dtMs: Long)')

# 5. Apply double score in placePiece
place_pattern = r'val delta = base \+ comboDelta \+ \(s\.streak \* 10\)'
new_place = 'var delta = base + comboDelta + (s.streak * 10)\n        if (s.doubleScoreRemainingMs > 0) delta *= 2'
if 'delta *= 2' not in content:
    content = re.sub(place_pattern, new_place, content)

with open('app/src/main/java/com/blockquest/domain/usecase/GameplayEngine.kt', 'w', encoding='utf-8') as f:
    f.write(content)

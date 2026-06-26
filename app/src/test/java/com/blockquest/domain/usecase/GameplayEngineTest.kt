package com.blockquest.domain.usecase

import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.LevelObjective
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.LevelType
import com.blockquest.domain.model.PieceShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Clock

class GameplayEngineTest {

    private lateinit var engine: GameplayEngine
    private val clock = Clock.System
    private val random = Random(42)

    @Before
    fun setup() {
        engine = GameplayEngine(clock, random)
    }

    @Test
    fun `startLevel initializes state correctly`() = runBlocking {
        val level = LevelSpec(
            levelId = "test_level",
            levelNumber = 1,
            worldIndex = 0,
            levelType = LevelType.Standard,
            objective = LevelObjective.ScoreTarget,
            targetScore = 1000,
            boardSize = 8 to 8
        )

        engine.startLevel(level, attempt = 1)

        val state = engine.state.value
        assertEquals("test_level", state.level?.levelId)
        assertEquals(0, state.score)
        assertEquals(3, state.tray.size)
        assertEquals(1, state.attemptNumber)
    }

    @Test
    fun `placing piece updates score and tray`() = runBlocking {
        val level = LevelSpec(
            levelId = "test_level",
            levelNumber = 1,
            worldIndex = 0,
            levelType = LevelType.Standard,
            objective = LevelObjective.ScoreTarget,
            targetScore = 1000,
            boardSize = 8 to 8
        )
        engine.startLevel(level, attempt = 1)
        val initialTray = engine.state.value.tray
        val piece = initialTray[0]

        val result = engine.place(0, Cell(0, 0))

        assertTrue(result is PlacementResult.Accepted)
        val state = engine.state.value
        assertEquals(piece.cells.size, state.score)
        assertEquals(2, state.tray.size)
    }

    @Test
    fun `clearing a line updates score with line bonus`() = runBlocking {
        val level = LevelSpec(
            levelId = "test_level",
            levelNumber = 1,
            worldIndex = 0,
            levelType = LevelType.Standard,
            objective = LevelObjective.ScoreTarget,
            targetScore = 1000,
            boardSize = 8 to 8
        )
        engine.startLevel(level, attempt = 1)

        // Manually fill 7 cells in the first row
        val board = engine.state.value.board
        for (x in 0 until 7) {
            board.set(x, 0, com.blockquest.domain.model.CellState.Occupied("manual"))
        }

        // Place a 1x1 dot at (7, 0) to complete the line
        // We need to ensure a 1x1 is in the tray or force it.
        // For this test, let's assume the random seed gives us something usable or we mock the selector.
        // Since we can't easily mock PiecePoolSelector yet, let's just use whatever is in the tray
        // and find a piece that can complete a line if possible, or just test the logic.
        
        // Actually, let's just test that the scoring logic for lines is correct if a line IS cleared.
        // The engine.place calls LineClearDetector.detect(s.board).
        
        // Let's force a 1x1 piece into the tray for testing if we can. 
        // We can't easily. Let's just use the first piece and fill the rest of the line.
        val piece = engine.state.value.tray[0]
        val (pw, ph) = piece.boundingBox
        
        // Clear board first
        board.clear()
        // Fill everything except where the piece will go to complete a row
        for (x in 0 until 8) {
            if (x !in 0 until pw) {
                board.set(x, 0, com.blockquest.domain.model.CellState.Occupied("pre"))
            }
        }
        
        val result = engine.place(0, Cell(0, 0))
        
        assertTrue(result is PlacementResult.Accepted)
        val accepted = result as PlacementResult.Accepted
        assertEquals(1, accepted.clearedRows.size)
        // Score = piece cells (pw*ph?) + line bonus (8 * 10)
        // For 1x1, score = 1 + 80 = 81.
        // Wait, PieceShape might not be 1x1.
        val expectedScore = piece.cells.size * 1 + (8 * 10) * 1 // Single multiplier
        assertEquals(expectedScore, engine.state.value.score)
    }
}

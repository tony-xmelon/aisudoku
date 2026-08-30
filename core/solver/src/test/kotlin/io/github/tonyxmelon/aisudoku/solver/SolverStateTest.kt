package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SolverStateTest {

    @Test
    fun `an empty grid starts with every digit possible everywhere`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        for (i in 0 until 81) {
            assertEquals(CandidateSet.ALL, state.candidatesAt(i))
        }
        assertEquals(0, state.solvedCount)
    }

    @Test
    fun `assigning a digit removes it from every peer`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        assertTrue(state.assign(0, 5))

        assertEquals(5, state.valueAt(0))
        assertFalse(5 in state.candidatesAt(1))   // same row
        assertFalse(5 in state.candidatesAt(9))   // same column
        assertFalse(5 in state.candidatesAt(10))  // same box
        assertTrue(5 in state.candidatesAt(80))   // unrelated
    }

    @Test
    fun `a cell reduced to one digit propagates to its own peers`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        // Leave only 9 possible at index 8 by filling the rest of row 0.
        for (d in 1..8) assertTrue(state.assign(d - 1, d))

        assertEquals(9, state.valueAt(8))
        assertFalse(9 in state.candidatesAt(17))  // 17 shares box 2 with cell 8
    }

    @Test
    fun `a digit with one place left in a unit is assigned there`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        // Remove 5 from every cell of row 0 except index 4.
        for (i in Row0.indices - 4) assertTrue(state.eliminate(i, 5))
        assertEquals(5, state.valueAt(4))
    }

    @Test
    fun `eliminating the last candidate is a contradiction`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        for (d in 1..8) assertTrue(state.eliminate(0, d))
        assertFalse(state.eliminate(0, 9))
    }

    @Test
    fun `a grid that breaks the rules cannot start a state`() {
        val broken = Grid.Empty.with(0, Cell.given(5)).with(1, Cell.given(5))
        assertNull(SolverState.from(broken))
    }

    @Test
    fun `guesses are ignored so only givens constrain the state`() {
        val grid = Grid.Empty.with(0, Cell.given(5)).with(40, Cell.guess(7))
        val state = assertNotNull(SolverState.from(grid))
        assertEquals(5, state.valueAt(0))
        assertNull(state.valueAt(40))
    }

    @Test
    fun `a copy does not share mutation with its source`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        val copy = state.copy()
        assertTrue(copy.assign(0, 5))
        assertEquals(5, copy.valueAt(0))
        assertNull(state.valueAt(0))
    }

    @Test
    fun `toGrid reports solved cells as guesses`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        assertTrue(state.assign(0, 5))
        val grid = state.toGrid()
        assertEquals(Cell.guess(5), grid[0])
        assertEquals(Cell.Empty, grid[1])
    }
}

/** Small helper so the test above reads clearly. */
private object Row0 {
    val indices = (0..8).toList()
}

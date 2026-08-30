package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.assertNotNull

/**
 * Builds a [SolverState] with hand-specified candidates, bypassing propagation.
 *
 * Every cell starts with all nine digits. Each pair overrides one cell. This produces
 * states that could not arise from a real puzzle, which is exactly the point: a
 * technique must be judged on the candidate pattern it claims to recognise, in isolation.
 */
fun stateWithCandidates(vararg overrides: Pair<Int, CandidateSet>): SolverState {
    val state = assertNotNull(SolverState.from(Grid.Empty))
    for ((index, candidates) in overrides) {
        for (digit in 1..9) {
            if (digit !in candidates) state.removeCandidate(index, digit)
        }
    }
    return state
}

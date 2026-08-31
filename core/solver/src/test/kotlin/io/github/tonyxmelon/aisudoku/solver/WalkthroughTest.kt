package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalkthroughTest {

    private val solution = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution

    @Test
    fun `an easy puzzle can be walked all the way to the answer`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(Puzzles.EASY))
        assertTrue(route.finishes)
        assertTrue(route.steps.isNotEmpty())
        assertEquals(Difficulty.EASY, route.hardest)
    }

    /** Every placement on the route has to be the true digit, or it is teaching a mistake. */
    @Test
    fun `every step of the route agrees with the solution`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(Puzzles.EASY))
        for (step in route.steps.filterIsInstance<Deduction.Placement>()) {
            assertEquals(
                solution[step.index].digit,
                step.digit,
                "step at ${step.index} teaches ${step.digit}",
            )
        }
    }

    /**
     * The route starts from where the user is, not from the printed givens. Otherwise it
     * walks them through work they have already done.
     */
    @Test
    fun `answers already filled in are not walked through again`() {
        val fromScratch = assertNotNull(TechniqueSolver.walkthrough(Puzzles.EASY))

        var partly = Puzzles.EASY
        val open = (0 until 81).filter { !partly[it].isFilled }
        for (i in open.take(open.size / 2)) {
            partly = partly.with(i, Cell.guess(solution[i].digit!!))
        }
        val remaining = assertNotNull(TechniqueSolver.walkthrough(partly))

        assertTrue(
            remaining.steps.size < fromScratch.steps.size,
            "half the puzzle is filled in, so the route must be shorter",
        )
        for (step in remaining.steps.filterIsInstance<Deduction.Placement>()) {
            assertTrue(!partly[step.index].isFilled, "step ${step.index} was already answered")
        }
    }

    /** A wrong answer must not be reasoned from, or the whole route inherits the mistake. */
    @Test
    fun `a wrong answer is ignored rather than built on`() {
        val open = (0 until 81).first { !Puzzles.EASY[it].isFilled }
        val wrong = (1..9).first { it != solution[open].digit }
        val route = assertNotNull(TechniqueSolver.walkthrough(Puzzles.EASY.with(open, Cell.guess(wrong))))

        for (step in route.steps.filterIsInstance<Deduction.Placement>()) {
            assertEquals(solution[step.index].digit, step.digit)
        }
    }

    @Test
    fun `a finished puzzle has no route left`() {
        val done = Grid.of(solution.cells.map { Cell.given(it.digit!!) })
        assertTrue(assertNotNull(TechniqueSolver.walkthrough(done)).isEmpty)
    }

    @Test
    fun `there is no route through a puzzle that has no answer`() {
        assertNull(TechniqueSolver.walkthrough(Puzzles.CONTRADICTORY))
    }

    /**
     * The four techniques do not finish Arto Inkala's 2012 puzzle - they find nothing at
     * all on it. Saying so is the honest outcome, and the walkthrough has to survive it.
     */
    @Test
    fun `a puzzle beyond the known techniques reports how far reasoning gets`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(Puzzles.HARDEST))
        assertTrue(!route.finishes)
    }

    @Test
    fun `every technique carries teaching material, and can be found by name`() {
        for (technique in Techniques.all) {
            assertTrue(technique.rule.isNotBlank(), "${technique.name} has no rule")
            assertTrue(technique.howTo.length > 80, "${technique.name} has no real how-to")
            assertEquals(technique, Techniques.byName(technique.name))
        }
        assertNull(Techniques.byName("Swordfish"))
    }
}

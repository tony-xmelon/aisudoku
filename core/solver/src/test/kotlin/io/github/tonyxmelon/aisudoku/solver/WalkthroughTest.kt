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
     * The whole point of the tutor: somebody holding an unfinished puzzle needs to be
     * walked to the end of it, not told the app gave up. Inkala's 2012 puzzle defeats
     * every pattern-based technique here from the very first move, so this is the case
     * that proves the route always arrives.
     */
    @Test
    fun `even the hardest puzzle is walked all the way to the end`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(Puzzles.HARDEST))
        assertTrue(route.finishes, "the route stopped short")

        val solution = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.HARDEST)).solution
        var grid = Puzzles.HARDEST
        for (step in route.steps) {
            if (step !is Deduction.Placement) continue
            assertEquals(solution[step.index].digit, step.digit, "step at ${step.index} is wrong")
            grid = grid.with(step.index, Cell.guess(step.digit))
        }
        assertTrue(grid.isComplete, "walking every step should fill the grid in")

        val named = route.steps.count { it.technique != TechniqueSolver.TRIED_OUT }
        println(
            "hardest: ${route.steps.size} steps, $named reasoned, ${route.triedOut} tried out, " +
                "hardest technique ${route.hardestTechnique}"
        )
    }

    /** Every puzzle the fixtures know about, walked to the end. */
    @Test
    fun `every solvable puzzle is walked to a complete grid`() {
        for ((label, puzzle) in listOf("easy" to Puzzles.EASY, "hardest" to Puzzles.HARDEST)) {
            val route = assertNotNull(TechniqueSolver.walkthrough(puzzle), label)
            assertTrue(route.finishes, "$label stopped short")
            var grid = puzzle
            for (step in route.steps.filterIsInstance<Deduction.Placement>()) {
                grid = grid.with(step.index, Cell.guess(step.digit))
            }
            assertTrue(grid.isComplete, "$label was not finished")
            assertTrue(grid.conflicts().isEmpty(), "$label was finished wrongly")
            println("$label: ${route.steps.size} steps, ${route.triedOut} tried out")
        }
    }

    @Test
    fun `every technique carries teaching material, and can be found by name`() {
        for (technique in Techniques.all) {
            assertTrue(technique.rule.isNotBlank(), "${technique.name} has no rule")
            assertTrue(technique.howTo.length > 150, "${technique.name} has no real how-to")
            assertEquals(technique, Techniques.byName(technique.name))
        }
        assertNull(
            Techniques.byName("Hidden X-wing"),
            "a name nobody has implemented must not resolve to something that sounds close",
        )
    }
}

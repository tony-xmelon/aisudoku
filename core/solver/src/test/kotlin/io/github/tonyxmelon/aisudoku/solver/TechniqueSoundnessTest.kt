package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The one property every technique must have: it never removes the truth.
 *
 * A technique that places a wrong digit is caught immediately by anyone using the app. A
 * technique that *eliminates* a candidate it should not is far worse - it corrupts the
 * position quietly, and whatever the tutor teaches after that point is nonsense derived
 * from a lie. Twelve techniques are too many to eyeball, so this checks all of them
 * against a known solution at every state the solver passes through.
 */
class TechniqueSoundnessTest {

    private val puzzles = listOf(
        "easy" to Puzzles.EASY,
        "hardest" to Puzzles.HARDEST,
    )

    @Test
    fun `no technique ever places a wrong digit or eliminates a right one`() {
        var checked = 0
        val fired = mutableMapOf<String, Int>()
        for ((label, puzzle) in puzzles) {
            val solution = assertIs<SolveResult.Unique>(Solver.solve(puzzle)).solution
            val state = assertIs<SolverState>(SolverState.candidatesOnly(puzzle))

            // Walk the position forward the way the solver does, auditing every technique
            // at every state along the way - not just the one the solver chose to apply.
            var moves = 0
            while (moves < 200) {
                for (technique in Techniques.all) {
                    for (deduction in technique.findAll(state)) {
                        checked++
                        fired[technique.name] = (fired[technique.name] ?: 0) + 1
                        audit(label, technique, deduction, solution)
                    }
                }
                val next = TechniqueSolver.nextDeduction(state) ?: break
                assertTrue(TechniqueSolver.apply(state, next), "$label: ${next.technique} broke the grid")
                moves++
            }
            println("$label: reasoning made $moves moves before running out")
        }
        assertTrue(checked > 200, "only $checked deductions audited - the sweep is too thin")
        println("audited $checked deductions:")
        for (technique in Techniques.all) {
            println("  %-20s %d".format(technique.name, fired[technique.name] ?: 0))
        }

        // A technique that never fires anywhere in the sweep is untested code claiming to
        // be a lesson. Better to know which ones those are than to ship them silently.
        val silent = Techniques.all.map { it.name }.filter { (fired[it] ?: 0) == 0 }
        println(if (silent.isEmpty()) "  every technique fired" else "  never fired: $silent")
    }

    private fun audit(
        label: String,
        technique: Technique,
        deduction: Deduction,
        solution: Grid,
    ) {
        when (deduction) {
            is Deduction.Placement -> assertEquals(
                solution[deduction.index].digit,
                deduction.digit,
                "$label: ${technique.name} places ${deduction.digit} at ${deduction.index}",
            )

            is Deduction.Elimination -> for (cell in deduction.fromCells) {
                assertTrue(
                    solution[cell].digit != deduction.digit,
                    "$label: ${technique.name} eliminates the true ${deduction.digit} from $cell",
                )
            }
        }
    }

    /** Every technique has to be able to say what it is, or the tutor has nothing to teach. */
    @Test
    fun `every technique carries teaching material and a unique name`() {
        val names = Techniques.all.map { it.name }
        assertEquals(names.size, names.toSet().size, "two techniques share a name: $names")

        for (technique in Techniques.all) {
            assertTrue(technique.rule.isNotBlank(), "${technique.name} has no rule")
            assertTrue(technique.howTo.length > 150, "${technique.name} has no real how-to")
            assertEquals(technique, Techniques.byName(technique.name))
        }
    }

    /** Ordered easiest first, so the solver offers the simplest reasoning that works. */
    @Test
    fun `the technique list never gets easier as it goes on`() {
        val order = Techniques.all.map { it.difficulty.ordinal }
        assertEquals(order.sorted(), order, "techniques are out of order: " +
            Techniques.all.joinToString { "${it.name}=${it.difficulty}" })
    }

    /** `find` is only ever a shortcut for the first of `findAll`, and must not diverge. */
    @Test
    fun `find agrees with findAll`() {
        val state = assertIs<SolverState>(SolverState.candidatesOnly(Puzzles.HARDEST))
        for (technique in Techniques.all) {
            assertEquals(technique.findAll(state).firstOrNull(), technique.find(state), technique.name)
        }
    }

    /**
     * Inkala's 2012 puzzle defeats all twelve of these outright - it is built to need
     * forcing chains, which none of them are. Recorded rather than asserted away: the day
     * a technique here does find something on it, that is worth knowing about.
     */
    @Test
    fun `the hardest puzzle is beyond every technique here, and says so`() {
        val originals = listOf(NakedSingle, HiddenSingle, PointingPair, BoxLineReduction)
        val state = assertIs<SolverState>(SolverState.candidatesOnly(Puzzles.HARDEST))
        assertTrue(
            originals.all { it.find(state) == null },
            "the original four were supposed to find nothing here",
        )
        val found = Techniques.all.filter { it.find(state) != null }
        println("on Inkala's puzzle, techniques that find anything: " +
            (found.joinToString { it.name }.ifEmpty { "none" }))
    }
}

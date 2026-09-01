package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A forcing chain is the one technique whose argument is a sequence rather than a
 * pattern, so it is the one that has to carry its working.
 *
 * These check the working, not merely that a trail came back: that replaying it really
 * does reach the wall it claims. A trail that does not lead where it says is worse than
 * no trail at all, because it teaches a step that does not follow.
 *
 * Taken from along the route rather than from the opening position. Nothing at all can be
 * deduced from the first position of this puzzle - that is what makes it the hardest one
 * in the set - so the chains only appear once other steps have opened it up.
 */
class ForcingChainTest {

    /** Each forcing chain on the route, with the position it was found in. */
    private fun trails(): List<Pair<SolverState, Deduction.Elimination>> {
        val solution = (Solver.solve(Puzzles.HARDEST) as SolveResult.Unique).solution
        val state = assertNotNull(
            SolverState.candidatesOnly(progressGrid(Puzzles.HARDEST, solution))
        )
        val route = assertNotNull(TechniqueSolver.walkthrough(Puzzles.HARDEST))

        val out = mutableListOf<Pair<SolverState, Deduction.Elimination>>()
        for (step in route.steps) {
            if (step is Deduction.Elimination &&
                step.technique == ForcingChain.name &&
                step.chain != null
            ) {
                out += state.copy() to step
            }
            TechniqueSolver.apply(state, step)
        }
        return out
    }

    @Test
    fun `the trail starts at the assumption it is disproving`() {
        val found = trails()
        assertTrue(found.isNotEmpty(), "this puzzle no longer exercises forcing chains")

        for ((_, step) in found) {
            val chain = assertNotNull(step.chain)
            val first = chain.links.first()
            assertEquals(step.fromCells.single(), first.index, "the trail starts elsewhere")
            assertEquals(step.digit, first.digit, "the trail assumes a different digit")

            // Each square appears once. A trail that revisits one is not a trail.
            assertEquals(
                chain.links.size,
                chain.links.map { it.index }.toSet().size,
                "the trail doubles back on itself",
            )
        }
    }

    @Test
    fun `replaying the trail really does reach the wall it claims`() {
        var checked = 0
        for ((position, step) in trails()) {
            val chain = assertNotNull(step.chain)
            checked++

            // Put every square of the trail where the trail says it goes, and see whether
            // the grid is really out of room where the chain says it is.
            val replay = position.copy()
            for (link in chain.links) replay.place(link.index, link.digit)

            if (chain.missing == null) {
                val wall = chain.deadEnd.single()
                assertTrue(
                    replay.candidatesAt(wall).isEmpty,
                    "the square the trail ends at can still hold " +
                        "${replay.candidatesAt(wall).digits()}",
                )
            } else {
                assertEquals(9, chain.deadEnd.size, "a unit has nine squares")
                val homes = chain.deadEnd.filter { chain.missing in replay.candidatesAt(it) }
                assertTrue(
                    homes.isEmpty(),
                    "${chain.missing} still fits at $homes in the unit the trail ends at",
                )
            }
        }
        assertTrue(checked > 0, "no chain carried a trail at all")
    }

    /**
     * Reported from the phone: "not all arrows can be traced back from the original cell".
     * Every square must hang off the assumption, or an arrow is drawn from nowhere.
     */
    @Test
    fun `every arrow leads back to the assumption`() {
        val found = trails()
        assertTrue(found.isNotEmpty(), "this puzzle no longer exercises forcing chains")

        for ((_, step) in found) {
            val chain = assertNotNull(step.chain)
            val root = chain.links.first()
            assertNull(root.from, "the assumption is forced by nothing")

            val parent = chain.links.associate { it.index to it.from }
            for (link in chain.links.drop(1)) {
                val fromCell = assertNotNull(link.from, "square ${link.index} has no arrow")
                assertTrue(
                    fromCell in parent,
                    "square ${link.index} points from ${link.from}, which is not on the trail",
                )

                // And walking those arrows backwards has to end at the assumption rather
                // than wandering off or going round.
                var at: Int? = link.index
                var steps = 0
                while (at != null && at != root.index && steps++ <= chain.links.size) {
                    at = parent[at]
                }
                assertEquals(root.index, at, "square ${link.index} does not lead back")
            }
        }
    }

    @Test
    fun `every square on the trail is reachable from the one before it`() {
        for ((_, step) in trails()) {
            val chain = assertNotNull(step.chain)

            // Consecutive links can sit a knight's move apart on the page: the second rule
            // of propagation works through a square that is only emptied of a candidate,
            // never filled, so that square is not on the trail. They still always meet
            // inside one square's neighbourhood, and an arrow between two squares with no
            // such connection would be drawing a step that did not happen.
            val seen = mutableSetOf(chain.links.first().index)
            for (link in chain.links.drop(1)) {
                val reachable = seen.any { earlier ->
                    link.index in Coordinates.peers[earlier] ||
                        Coordinates.peers[link.index].any { it in Coordinates.peers[earlier] }
                }
                assertTrue(reachable, "square ${link.index} is unreachable from the trail")
                seen += link.index
            }
        }
    }
}

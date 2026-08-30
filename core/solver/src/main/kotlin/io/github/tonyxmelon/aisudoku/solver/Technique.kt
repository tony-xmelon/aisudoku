package io.github.tonyxmelon.aisudoku.solver

/**
 * One human solving method.
 *
 * Implementations must be pure: given a state, return the first deduction they can find,
 * or null. They never mutate the state — [TechniqueSolver] decides whether to apply what
 * they find, which is what lets the hint engine describe a step without taking it.
 */
interface Technique {
    val name: String
    val difficulty: Difficulty
    fun find(state: SolverState): Deduction?
}

/**
 * Every technique, easiest first. Order matters: a hint should offer the simplest
 * reasoning that works, not the cleverest.
 */
val ALL_TECHNIQUES: List<Technique> = listOf(
    NakedSingle,
    HiddenSingle,
    PointingPair,
    BoxLineReduction,
)

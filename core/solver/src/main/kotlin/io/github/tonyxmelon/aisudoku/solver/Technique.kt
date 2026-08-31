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

    /** The rule itself, in one sentence. What is true, regardless of any puzzle. */
    val rule: String

    /**
     * How to go looking for it on a page.
     *
     * Written to be read while holding a pencil, not while reading about sudoku. The app
     * can already finish any puzzle it can read; the only reason to name a technique at
     * all is so the user can find the next one without it.
     */
    val howTo: String

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

/** Looking a technique up by the name a [Deduction] reports. */
object Techniques {
    val all: List<Technique> get() = ALL_TECHNIQUES

    fun byName(name: String): Technique? = ALL_TECHNIQUES.firstOrNull { it.name == name }
}

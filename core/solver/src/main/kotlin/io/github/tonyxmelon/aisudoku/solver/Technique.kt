package io.github.tonyxmelon.aisudoku.solver

/**
 * One human solving method.
 *
 * Implementations must be pure: given a state, report what they can see and change
 * nothing. [TechniqueSolver] decides whether to apply what they find, which is what lets
 * the tutor describe a step without taking it.
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

    /**
     * Everything this technique can see in [state], not just the first thing.
     *
     * The solver only ever needs the first, but the tutor lets the user browse a
     * technique and step through every place it applies right now - which is how you
     * learn to spot one, rather than learning that one existed.
     */
    fun findAll(state: SolverState): List<Deduction>

    /** The first available deduction, which is all the solver needs to make progress. */
    fun find(state: SolverState): Deduction? = findAll(state).firstOrNull()
}

/**
 * Every technique, easiest first.
 *
 * Order matters twice over: the solver offers the simplest reasoning that works rather
 * than the cleverest, and the same order is the one worth learning them in.
 */
val ALL_TECHNIQUES: List<Technique> = listOf(
    NakedSingle,
    HiddenSingle,
    NakedPair,
    HiddenPair,
    PointingPair,
    BoxLineReduction,
    NakedTriple,
    HiddenTriple,
    XWing,
    YWing,
    XyzWing,
    Swordfish,
    ForcingChain,
)

/** Looking a technique up by the name a [Deduction] reports. */
object Techniques {
    val all: List<Technique> get() = ALL_TECHNIQUES

    fun byName(name: String): Technique? = ALL_TECHNIQUES.firstOrNull { it.name == name }
}

/** Every way of choosing [size] items, in the order they appear. */
internal fun <T> combinations(items: List<T>, size: Int): List<List<T>> {
    if (size == 0) return listOf(emptyList())
    if (items.size < size) return emptyList()
    val out = mutableListOf<List<T>>()
    val current = ArrayList<T>(size)

    fun walk(from: Int) {
        if (current.size == size) {
            out += ArrayList(current)
            return
        }
        for (i in from until items.size) {
            if (items.size - i < size - current.size) break
            current += items[i]
            walk(i + 1)
            current.removeAt(current.size - 1)
        }
    }
    walk(0)
    return out
}

/** The union of the candidates of several cells. */
internal fun SolverState.candidateUnion(cells: Collection<Int>): CandidateSet =
    CandidateSet(cells.fold(0) { bits, i -> bits or candidatesAt(i).bits })

/** Cells of a unit that are still open. */
internal fun SolverState.openCells(unit: List<Int>): List<Int> =
    unit.filter { valueAt(it) == null }

/** Where a digit can still go inside a unit. */
internal fun SolverState.placesFor(unit: List<Int>, digit: Int): List<Int> =
    unit.filter { valueAt(it) == null && digit in candidatesAt(it) }

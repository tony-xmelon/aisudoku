package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Grid

/**
 * Puzzles shared across solver and technique tests.
 *
 * The solution counts below were verified with an independent solver before these
 * fixtures were written, so a test failing on them indicates a bug in this code.
 */
object Puzzles {

    /** 30 givens, exactly one solution. Solvable by naked and hidden singles alone. */
    val EASY: Grid = Grid.fromRows(
        "53..7....",
        "6..195...",
        ".98....6.",
        "8...6...3",
        "4..8.3..1",
        "7...2...6",
        ".6....28.",
        "...419..5",
        "....8..79",
    )

    /** Arto Inkala's 2012 puzzle: 21 givens, exactly one solution, a worst case for search. */
    val HARDEST: Grid = Grid.fromRows(
        "8........",
        "..36.....",
        ".7..9.2..",
        ".5...7...",
        "....457..",
        "...1...3.",
        "..1....68",
        "..85...1.",
        ".9....4..",
    )

    /**
     * EASY with the two givens of row 0 removed, leaving 28. Verified to have more than
     * one solution, so the solver must report ambiguity rather than pick a favourite.
     */
    val AMBIGUOUS: Grid = Grid.fromRows(
        "....7....",
        "6..195...",
        ".98....6.",
        "8...6...3",
        "4..8.3..1",
        "7...2...6",
        ".6....28.",
        "...419..5",
        "....8..79",
    )

    /** Two fives in the top row. */
    val CONTRADICTORY: Grid = Grid.fromRows(
        "55.......",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
    )
}

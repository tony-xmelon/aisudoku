package io.github.tonyxmelon.aisudoku.model

/**
 * Index arithmetic for a 9x9 grid stored row-major in `0..80`.
 *
 * Everything here is a precomputed table. These lookups sit in the innermost
 * loop of the solver, so they are built once rather than recomputed.
 */
object Coordinates {

    const val SIZE = 9
    const val CELL_COUNT = SIZE * SIZE

    fun rowOf(index: Int): Int = index / SIZE

    fun colOf(index: Int): Int = index % SIZE

    fun boxOf(index: Int): Int = (rowOf(index) / 3) * 3 + colOf(index) / 3

    fun indexOf(row: Int, col: Int): Int = row * SIZE + col

    /** `rowIndices[r]` holds the nine indices of row `r`, left to right. */
    val rowIndices: List<List<Int>> =
        (0 until SIZE).map { r -> (0 until SIZE).map { c -> indexOf(r, c) } }

    /** `colIndices[c]` holds the nine indices of column `c`, top to bottom. */
    val colIndices: List<List<Int>> =
        (0 until SIZE).map { c -> (0 until SIZE).map { r -> indexOf(r, c) } }

    /** `boxIndices[b]` holds the nine indices of box `b`, left to right, top to bottom. */
    val boxIndices: List<List<Int>> =
        (0 until SIZE).map { b ->
            val firstRow = (b / 3) * 3
            val firstCol = (b % 3) * 3
            (0 until 3).flatMap { dr -> (0 until 3).map { dc -> indexOf(firstRow + dr, firstCol + dc) } }
        }

    /** All 27 units: nine rows, then nine columns, then nine boxes. */
    val units: List<List<Int>> = rowIndices + colIndices + boxIndices

    /** `unitsOf[i]` holds the three units containing cell `i`: its row, column and box. */
    val unitsOf: List<List<List<Int>>> =
        (0 until CELL_COUNT).map { i ->
            listOf(rowIndices[rowOf(i)], colIndices[colOf(i)], boxIndices[boxOf(i)])
        }

    /** The 20 cells sharing a row, column or box with cell `i`, excluding `i` itself. */
    val peers: List<Set<Int>> =
        (0 until CELL_COUNT).map { i -> unitsOf[i].flatten().toSet() - i }
}

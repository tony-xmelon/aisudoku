package io.github.tonyxmelon.aisudoku.solver

/**
 * The digits `1..9` still possible in a cell, held as bits 0..8 of an `Int`.
 *
 * A value class, so it costs exactly one `Int` at runtime and allocates nothing.
 * The solver reads and rebuilds these constantly during search.
 */
@JvmInline
value class CandidateSet(val bits: Int) {

    operator fun contains(digit: Int): Boolean = bits and maskOf(digit) != 0

    fun plus(digit: Int): CandidateSet = CandidateSet(bits or maskOf(digit))

    fun minus(digit: Int): CandidateSet = CandidateSet(bits and maskOf(digit).inv())

    val size: Int get() = bits.countOneBits()

    val isEmpty: Boolean get() = bits == 0

    /** The only remaining digit, or null when there are none or more than one. */
    val single: Int? get() = if (size == 1) bits.countTrailingZeroBits() + 1 else null

    fun digits(): List<Int> = (1..9).filter { it in this }

    override fun toString(): String = digits().joinToString(prefix = "{", postfix = "}")

    companion object {
        val ALL: CandidateSet = CandidateSet(0b1_1111_1111)
        val NONE: CandidateSet = CandidateSet(0)

        fun of(vararg digits: Int): CandidateSet =
            digits.fold(NONE) { acc, d -> acc.plus(d) }

        private fun maskOf(digit: Int): Int {
            require(digit in 1..9) { "digit must be 1..9 but was $digit" }
            return 1 shl (digit - 1)
        }
    }
}

package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * One digit, coloured through its forced pairs until the colouring contradicts itself.
 *
 * Wherever a digit has exactly two homes left in a unit, one of them holds it and the
 * other does not - a see-saw. Link every such pair and the board breaks into chains, and
 * a chain can be painted in two colours so that one colour is entirely true and the other
 * entirely false. Which is which is unknown, and two things follow anyway:
 *
 * - if one colour turns up twice in the same unit, that colour is the false one, and the
 *   digit goes from every square wearing it;
 * - any square outside the chain that can see both colours cannot hold the digit, because
 *   one of the two colours is true wherever it appears.
 *
 * This is the first technique here that reasons about a whole board rather than a corner
 * of one, and it replaces a great many forcing chains with something a person can see.
 */
object SimpleColouring : Technique {

    override val name = "Simple colouring"
    override val difficulty = Difficulty.VERY_HARD

    override val rule = "Chain a digit through the units where it has only two homes, " +
        "colour the chain in two alternating colours, and one colour is true throughout."

    override val howTo = "Take one digit and mark every unit where it has exactly two " +
        "squares left. Those two are a see-saw: one of them is the digit and the other is " +
        "not.\n\nStart anywhere and colour a square, say, blue. Colour its see-saw partner " +
        "green, then whatever that one is paired with blue again, and keep going until the " +
        "chain runs out. Every blue square is true or every green square is - you cannot " +
        "tell which, and that is enough for two things.\n\nFirst: if two squares of the " +
        "same colour turn up in one row, column or box, that colour is impossible, so the " +
        "digit goes from every square wearing it and the other colour is the answer " +
        "throughout.\n\nSecond: any square outside the chain that can see a blue and a " +
        "green can give the digit up, because one of those two is holding it whichever way " +
        "the chain falls.\n\nIt is slow with a pencil and worth it: one colouring often " +
        "clears a digit off half the board."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        for (digit in 1..9) {
            for (chain in chains(state, digit)) {
                out += trapped(state, digit, chain)
                out += seenByBoth(state, digit, chain)
            }
        }
        return out
    }

    /** One chain, painted: the squares of each colour. */
    private data class Painted(val blue: Set<Int>, val green: Set<Int>) {
        val all: Set<Int> get() = blue + green
    }

    /**
     * Every chain of forced pairs for [digit], each painted in two colours.
     *
     * A chain that will not take two colours is dropped rather than painted badly. The
     * links join squares that must be opposite, so a chain that comes back round to itself
     * the wrong way is not a see-saw at all - and everything this technique concludes
     * rests on the painting being right.
     */
    private fun chains(state: SolverState, digit: Int): List<Painted> {
        val links = HashMap<Int, MutableSet<Int>>()
        for (unit in Coordinates.units) {
            val places = state.strongPlaces(unit, digit) ?: continue
            links.getOrPut(places[0]) { mutableSetOf() } += places[1]
            links.getOrPut(places[1]) { mutableSetOf() } += places[0]
        }

        val painted = mutableListOf<Painted>()
        val colour = HashMap<Int, Boolean>()
        for (start in links.keys.sorted()) {
            if (start in colour) continue

            val blue = mutableSetOf(start)
            val green = mutableSetOf<Int>()
            colour[start] = true
            var consistent = true
            val queue = ArrayDeque(listOf(start))
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                val wanted = !colour.getValue(at)
                for (next in links[at].orEmpty()) {
                    val already = colour[next]
                    if (already != null) {
                        // Two squares joined by a see-saw must be opposite colours. If the
                        // links say otherwise the chain is not two-colourable, and every
                        // conclusion drawn from painting it would be worthless. Rather
                        // than reason from a bad painting, drop the chain.
                        if (already != wanted) consistent = false
                        continue
                    }
                    colour[next] = wanted
                    if (wanted) blue += next else green += next
                    queue += next
                }
            }
            // A chain of two squares says only that one of them holds the digit, which is
            // what the unit it came from already said. It takes three to be worth painting.
            if (consistent && blue.size + green.size > 2) painted += Painted(blue, green)
        }
        return painted
    }

    /** A colour that appears twice in one unit is the false one. */
    private fun trapped(state: SolverState, digit: Int, chain: Painted): List<Deduction> {
        val out = mutableListOf<Deduction>()
        for ((colour, name) in listOf(chain.blue to "one", chain.green to "the other")) {
            val clash = Coordinates.units.firstOrNull { unit ->
                unit.count { it in colour } > 1
            } ?: continue

            val targets = colour.filter { digit in state.candidatesAt(it) }.toSet()
            if (targets.isEmpty()) continue

            out += Deduction.Elimination(
                technique = this.name,
                difficulty = difficulty,
                explanation = "Chaining $digit through the units where it has only two " +
                    "homes leaves these squares in two alternating colours, one of which " +
                    "is true throughout. Two squares of $name colour sit in the same " +
                    "${unitName(clash)}, which cannot be - so that colour is the false " +
                    "one, and $digit goes from every square wearing it.",
                supportingCells = chain.all,
                digit = digit,
                fromCells = targets,
            )
            return out
        }
        return out
    }

    /** A square outside the chain that sees both colours cannot hold the digit. */
    private fun seenByBoth(state: SolverState, digit: Int, chain: Painted): List<Deduction> {
        val targets = (0 until Coordinates.CELL_COUNT).filter { cell ->
            cell !in chain.all &&
                state.valueAt(cell) == null &&
                digit in state.candidatesAt(cell) &&
                chain.blue.any { it in Coordinates.peers[cell] } &&
                chain.green.any { it in Coordinates.peers[cell] }
        }.toSet()
        if (targets.isEmpty()) return emptyList()

        return listOf(
            Deduction.Elimination(
                technique = name,
                difficulty = difficulty,
                explanation = "Chaining $digit through the units where it has only two " +
                    "homes leaves these squares in two alternating colours, and one colour " +
                    "is true throughout. These squares can see both colours, so whichever " +
                    "way the chain falls one of the squares they see holds $digit - which " +
                    "means they cannot.",
                supportingCells = chain.all,
                digit = digit,
                fromCells = targets,
            )
        )
    }
}

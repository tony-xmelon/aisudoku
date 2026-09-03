package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.recognize.Ink
import io.github.tonyxmelon.aisudoku.solver.Chain
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
/*
 * Everything drawn on top of the photograph, and nothing that arranges it.
 *
 * Split out of PuzzleScreen, which had grown to 1462 lines and held five unrelated jobs.
 * This is the one with no Compose layout in it at all: it is Canvas work, and it sat in
 * two separate stretches of that file with four hundred lines of unrelated user interface
 * between them.
 */
/**
 * A forcing chain: the assumption, what it forces, and the wall it hits.
 *
 * The one argument in the app whose order is the argument, so it is the one drawn with
 * arrows. Every square on the trail carries the digit it is forced to hold; the squares
 * at the end - one that can hold nothing, or a whole unit with nowhere left to put some
 * digit - are red, because that is the impossibility the whole trail was built to reach.
 *
 * Order of drawing matters. The digits are punched out last so that they cut cleanly
 * through both the tint and any arrow crossing them, which is what keeps a trail of eight
 * arrows legible on a photograph of a page.
 */
private fun DrawScope.drawChain(chain: Chain, squares: Squares, measurer: TextMeasurer) {
    for (index in chain.deadEnd) {
        squares.fill(this, index, Overlays.incorrect.copy(alpha = 0.34f))
    }
    for (link in chain.links) {
        squares.fill(this, link.index, Overlays.evidence.copy(alpha = 0.45f))
    }

    // One arrow per square, drawn from whatever forced it. The trail branches - the wall
    // usually rests on more than one line of consequence - so this is a tree and not a
    // line, and drawing it as a line would be drawing an argument that was not made.
    val on = chain.links.mapTo(mutableSetOf()) { it.index }
    for (link in chain.links) {
        val parent = link.from ?: continue
        if (parent in on) drawArrow(squares.centre(parent), squares.centre(link.index), squares.unit)
    }

    // And into the wall itself, when the wall is one square. A whole unit has no centre
    // worth pointing at, and the block of red says where it is well enough.
    val wall = chain.deadEnd.singleOrNull()
    if (wall != null) {
        chain.deadEndFrom?.let {
            drawArrow(squares.centre(it), squares.centre(wall), squares.unit)
        }
        // Crossed out, because the point of that square is that nothing goes in it. A red
        // tint alone reads as "wrong answer here", which is the opposite of what it means.
        val at = squares.topLeft(wall)
        val cell = squares.size(wall)
        val inset = squares.unit * 0.3f
        val stroke = squares.unit * 0.07f
        val colour = Overlays.incorrect
        drawLine(
            colour,
            at + Offset(inset, inset),
            at + Offset(cell.width - inset, cell.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            colour,
            at + Offset(cell.width - inset, inset),
            at + Offset(inset, cell.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }

    for (link in chain.links) drawReadDigit(measurer, squares, link.index, link.digit)

    // Numbered, because arrows alone cannot be followed once a dozen of them cross. The
    // trail is a tree, so the numbers are the order things were forced rather than a
    // single line - but every square's number is larger than its parent's, so counting up
    // always walks away from the assumption and never back towards it.
    for ((step, link) in chain.links.withIndex()) {
        drawCorner(measurer, squares, link.index, "${step + 1}")
    }

    // Every square in the dead end the missing digit could have gone in, marked with it.
    //
    // One mark was not enough. A whole unit in red and a sentence saying the digit has
    // nowhere left to go is a claim, not a picture: it does not say where the digit could
    // have gone, and so it cannot show that each of those places has just been taken by
    // one of the arrows. Reported from the phone as "why can't 5 be in r1c2?", which the
    // grid had no answer to.
    val ending = chain.missing?.let { missing ->
        for (cell in chain.blocked) drawGhost(measurer, squares, cell, missing)
        chain.blocked
    } ?: setOfNotNull(wall)

    // The wall is the last thing that happens, so it all carries the next number.
    for (cell in ending) drawCorner(measurer, squares, cell, "${chain.links.size + 1}")
}

/** A small number in the corner of a square, for the order of a trail. */
private fun DrawScope.drawCorner(
    measurer: TextMeasurer,
    squares: Squares,
    index: Int,
    text: String,
) {
    val layout = measurer.measure(
        text,
        style = TextStyle(
            color = Color.White,
            fontSize = (squares.unit * 0.26f / 2.2f).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    val at = squares.topLeft(index)
    drawText(layout, topLeft = at + Offset(squares.unit * 0.07f, squares.unit * 0.04f))
}

/**
 * The digit that can no longer go here, in the colour of the wall it is part of.
 *
 * Outlined rather than solid, because it is drawn over a photograph of a square that
 * already has pencil marks in it, and a solid glyph hid the very thing the reader was
 * checking it against. Not struck through either: it sits in a square already tinted red,
 * inside a unit already tinted red, and a third mark saying the same thing only crowds
 * the pencil marks underneath.
 */
private fun DrawScope.drawGhost(
    measurer: TextMeasurer,
    squares: Squares,
    index: Int,
    digit: Int,
) {
    val layout = measurer.measure(
        digit.toString(),
        style = TextStyle(
            color = Overlays.incorrect,
            fontSize = (squares.unit * 0.62f / 2.2f).sp,
            fontWeight = FontWeight.Bold,
            drawStyle = Stroke(width = squares.unit * 0.045f),
        ),
    )
    val at = squares.topLeft(index)
    val cell = squares.size(index)
    drawText(
        layout,
        topLeft = Offset(
            at.x + (cell.width - layout.size.width) / 2f,
            at.y + (cell.height - layout.size.height) / 2f,
        ),
    )
}

/**
 * One arrow, stopping short at both ends so it does not run over the digits it joins.
 *
 * Set a little to one side of the line between the two squares rather than straight along
 * it. A square that forces two others in opposite directions was drawing two arrows down
 * the same line, tail to tail, which reads as one long arrow with a head at each end
 * passing through the square - and then nothing in the picture leads back to where the
 * chain began. The offset is taken from each arrow's own direction, so the two land on
 * opposite sides of the square and separate rather than doubling up.
 */
private fun DrawScope.drawArrow(from: Offset, to: Offset, unit: Float) {
    val step = to - from
    val length = hypot(step.x, step.y)
    if (length < 1f) return

    val direction = Offset(step.x / length, step.y / length)
    val aside = Offset(-direction.y, direction.x) * (unit * 0.13f)
    val clear = unit * 0.34f
    if (length <= clear * 2f + unit * 0.1f) return

    val start = from + direction * clear + aside
    val end = to - direction * clear + aside
    val colour = Color.White.copy(alpha = 0.92f)
    drawLine(colour, start, end, strokeWidth = unit * 0.045f, cap = StrokeCap.Round)

    val head = unit * 0.2f
    val back = Offset(-direction.x, -direction.y)
    for (turn in listOf(0.45f, -0.45f)) {
        val wing = Offset(
            back.x * cos(turn) - back.y * sin(turn),
            back.x * sin(turn) + back.y * cos(turn),
        )
        drawLine(colour, end, end + wing * head, strokeWidth = unit * 0.045f, cap = StrokeCap.Round)
    }
}

/**
 * The straightened photograph with help drawn on top.
 *
 * The working surface is the rectified image rather than the original: it is the same
 * photograph, and a square grid makes the overlay a matter of dividing by nine.
 *
 * **The photograph's size comes from the window and from nothing else.** It used to take
 * whatever the controls left over, so it grew and shrank as the text under it changed -
 * switching layer moved the grid under your finger. The controls now scroll inside their
 * own space instead.
 */
internal fun DrawScope.drawOverlay(state: PuzzleState, measurer: TextMeasurer) {
    // Everything the overlay draws goes into one offscreen layer, so that a digit can be
    // punched out of the tint above it. Clearing straight onto the canvas would take the
    // photograph with it, since the photograph is the content underneath.
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawOverlayInLayer(state, measurer)
        canvas.restore()
    }
}

private fun DrawScope.drawOverlayInLayer(state: PuzzleState, measurer: TextMeasurer) {
    val squares = Squares(state.lines, size.width, size.height)

    if (state.overlay == OverlayMode.READING) {
        drawReading(state, measurer, squares)
    }

    // The full solution covers squares the user has already answered, so their own
    // writing would show through every digit. One scrim over the whole grid keeps the
    // answer legible without giving any single square a colour of its own.
    if (state.overlay == OverlayMode.SOLUTION) {
        drawRect(Color(0x59000000), Offset.Zero, size)
    }

    for (index in state.evidenceCells()) {
        squares.fill(this, index, Overlays.evidence.copy(alpha = 0.28f))
    }

    state.chain()?.let { drawChain(it, squares, measurer) }

    for ((index, digit) in state.overlayDigits()) {
        val colour = Overlays.colour(digit.role)
        when (digit.role) {
            // The paper already shows the right digit, so a tint is all that is needed.
            OverlayRole.CORRECT -> squares.fill(this, index, colour.copy(alpha = 0.32f))

            // Tint, and then what the app read, punched out of it in the corner.
            // Drawn exactly as the reading layer draws it, because it is the same
            // statement: this is what I saw there. Without it a misreading is
            // indistinguishable from the app marking a right answer wrong, which is
            // how it was first reported.
            OverlayRole.INCORRECT -> {
                squares.fill(this, index, colour.copy(alpha = 0.42f))
                drawReadDigit(measurer, squares, index, digit.digit)
            }

            OverlayRole.SOLUTION, OverlayRole.HINT ->
                drawCentred(measurer, squares, index, digit.digit.toString(), colour)

            // Exactly what the reading layer does with handwriting, because it is the
            // same statement: there is a digit here and this is what it says. Drawn on
            // one square rather than on all eighty-one.
            OverlayRole.WRITTEN -> {
                squares.fill(this, index, colour.copy(alpha = 0.52f))
                drawReadDigit(measurer, squares, index, digit.digit)
            }
        }
    }

    // Confidence, drawn wherever it is worth knowing: on every square in the reading
    // layer, and on the squares the reader flagged in the others - which is what marks
    // them now that the ring is gone.
    //
    // The ring was amber and sat inset from the square's edge, which put its bottom
    // stroke straight through the bar that says how unsure the app actually was. Two
    // marks for one fact, and the coarser of the two hid the finer.
    state.reports?.let { reports ->
        val marked = if (state.overlay == OverlayMode.READING) {
            (0 until 81).filter { reports.getOrNull(it)?.digit != null }
        } else {
            state.uncertainCells.sorted()
        }
        for (index in marked) {
            reports.getOrNull(index)?.let {
                drawConfidence(squares, index, it.confidence, index in state.uncertainCells)
            }
        }
    }

    // The square a hint or a step is pointing at, before it says what goes in it.
    state.focusCell()?.let { index ->
        squares.outline(this, index, Overlays.hint, squares.unit * 0.06f, inset = 0.04f)
    }

    state.selectedCell?.let { index ->
        drawRect(Color.White, squares.topLeft(index), squares.size(index), style = Stroke(width = 4f))
    }
}

/**
 * What the reader made of every square, drawn over the photograph.
 *
 * A tint says what the square was taken to be. It reads at a glance in a way an outline
 * does not, and the price is that everything else drawn here has to stay out of the way:
 *
 *  - the digit is **punched out of the tint**, big and centred, so it adds no ink and
 *    hides nothing. A solid chip used to sit at the top-right, which is exactly where the
 *    candidate marks are written;
 *  - the confidence bar is a hairline along the very bottom edge, drawn by
 *    [drawOverlay] so that the squares the reader flagged carry one in every layer.
 *
 * This layer exists because after a read there was otherwise no way to see what the app
 * had decided - only what it had decided to do about it.
 */
private fun DrawScope.drawReading(state: PuzzleState, measurer: TextMeasurer, squares: Squares) {
    val reports = state.reports
    for (index in 0 until 81) {
        val report = reports?.getOrNull(index)

        // Without reports - a puzzle reopened from history - fall back to the grid, which
        // still knows print from handwriting even though the numbers are gone.
        val ink = report?.ink ?: when (state.grid[index].source) {
            CellSource.GIVEN -> Ink.PRINTED
            CellSource.GUESS -> Ink.ANSWER
            CellSource.EMPTY -> Ink.NONE
        }
        val digit = report?.digit ?: state.grid[index].digit

        val colour = when (ink) {
            Ink.PRINTED -> Overlays.printed
            Ink.ANSWER -> Overlays.written
            Ink.MARK -> Overlays.marks
            Ink.NONE -> null
        } ?: continue

        // Heavier than it looks: on a square holding a digit most of this is about to be
        // cut away again. Pencil marks stay faint - there are forty of them on a busy page
        // and they are the least interesting thing on it.
        val marks = ink == Ink.MARK
        squares.fill(this, index, colour.copy(alpha = if (marks) 0.30f else 0.52f))

        if (digit != null) drawReadDigit(measurer, squares, index, digit)

    }
}

/**
 * How sure the classifier was, as a hairline along the very bottom edge.
 *
 * Thin and full width, so it reads as a gauge rather than as another thing sitting on
 * the page.
 */
private fun DrawScope.drawConfidence(
    squares: Squares,
    index: Int,
    confidence: Float,
    flagged: Boolean,
) {
    val at = squares.topLeft(index)
    val cell = squares.size(index)

    // Thick where the user is being asked about the square, because on a nine by nine
    // grid a hairline is not something anyone is going to find. In the reading layer
    // every square has one and they stay out of the way.
    val height = squares.unit * if (flagged) 0.13f else 0.045f
    val inset = squares.unit * 0.06f
    val width = cell.width - inset * 2
    val top = at.y + cell.height - height - inset * 0.5f

    // Green, amber, red by how likely the classifier thought its answer was - except that
    // a flagged square is never green. A square can be flagged with the classifier
    // perfectly confident: the solver threw the digit out because a clump of candidate
    // marks was not a printed digit at all, which the classifier had no way to know. Its
    // confidence is then beside the point and must not read as reassurance.
    val colour = when {
        confidence < 0.6f -> Overlays.incorrect
        flagged || confidence < 0.9f -> Overlays.uncertain
        else -> Overlays.correct
    }

    drawRect(Color(0x66000000), Offset(at.x + inset, top), Size(width, height))
    drawRect(
        color = colour,
        topLeft = Offset(at.x + inset, top),
        size = Size(width * confidence.coerceIn(0f, 1f), height),
    )
}

/**
 * The digit the app read, punched out of the tint as a hole.
 *
 * It adds no ink at all: the glyph is the one part of the square where the tint has been
 * cleared, so what shows through it is the photograph itself. That is the whole point -
 * every other way of putting the app's digit on the page covered some of the page.
 *
 * Big and centred, the size of the digits the solution draws. A small hole in a light
 * tint is a small amount of contrast, and the way to buy contrast without covering
 * anything is to make the hole bigger rather than the tint heavier.
 *
 * Centring it over the paper's own digit turns out to be the point rather than the
 * problem. Where the reading is right the two coincide and the square simply reads as a
 * clean digit standing out of a tinted surround. Where it is wrong the paper's strokes
 * come out of the glyph and into the tint, which is far more visible than two small
 * digits sitting side by side ever were.
 *
 * Only works inside an offscreen layer; see [drawOverlay].
 */
private fun DrawScope.drawReadDigit(
    measurer: TextMeasurer,
    squares: Squares,
    index: Int,
    digit: Int,
) {
    val layout = measurer.measure(
        digit.toString(),
        style = TextStyle(
            // Irrelevant under BlendMode.Clear - only the glyph's shape is used.
            color = Color.Black,
            fontSize = (squares.unit * 0.62f / 2.2f).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    val at = squares.topLeft(index)
    val cell = squares.size(index)
    drawText(
        layout,
        topLeft = Offset(
            at.x + (cell.width - layout.size.width) / 2f,
            at.y + (cell.height - layout.size.height) / 2f,
        ),
        blendMode = BlendMode.Clear,
    )
}

/**
 * The 81 rectangles of the photograph on screen.
 *
 * Taken from the grid lines the extractor actually fitted, not from dividing by nine.
 * Paper is not flat, which is why those lines are fitted in the first place; drawing on
 * ninths puts the tints and digits a few pixels off exactly where the page is most bowed.
 */
private class Squares(lines: GridLines, width: Float, height: Float) {
    private val xs = FloatArray(10) { lines.vertical[it] * width }
    private val ys = FloatArray(10) { lines.horizontal[it] * height }

    /** A typical square, for text and strokes that should not vary from cell to cell. */
    val unit: Float = minOf(width, height) / 9f

    fun topLeft(index: Int) = Offset(xs[index % 9], ys[index / 9])

    fun centre(index: Int) = Offset(
        (xs[index % 9] + xs[index % 9 + 1]) / 2f,
        (ys[index / 9] + ys[index / 9 + 1]) / 2f,
    )

    fun size(index: Int) = Size(
        xs[index % 9 + 1] - xs[index % 9],
        ys[index / 9 + 1] - ys[index / 9],
    )

    fun fill(scope: DrawScope, index: Int, colour: Color) =
        scope.drawRect(colour, topLeft(index), size(index))

    fun outline(scope: DrawScope, index: Int, colour: Color, width: Float, inset: Float) {
        val at = topLeft(index)
        val cell = size(index)
        scope.drawRect(
            color = colour,
            topLeft = at + Offset(cell.width * inset, cell.height * inset),
            size = Size(cell.width * (1 - inset * 2), cell.height * (1 - inset * 2)),
            style = Stroke(width = width),
        )
    }
}

/** A digit in the middle of its square. */
private fun DrawScope.drawCentred(
    measurer: TextMeasurer,
    squares: Squares,
    index: Int,
    text: String,
    colour: Color,
) {
    val layout = measurer.measure(
        text,
        style = TextStyle(
            color = colour,
            fontSize = (squares.unit * 0.62f / 2.2f).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    val at = squares.topLeft(index)
    val cell = squares.size(index)
    drawText(
        layout,
        topLeft = Offset(
            at.x + (cell.width - layout.size.width) / 2f,
            at.y + (cell.height - layout.size.height) / 2f,
        ),
    )
}

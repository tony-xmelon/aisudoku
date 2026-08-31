package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.recognize.Ink

/**
 * The straightened photograph with help drawn on top.
 *
 * The working surface is the rectified image rather than the original: it is the same
 * photograph, and a square grid makes the overlay a matter of dividing by nine.
 *
 * The screen is a fixed three-part column - bar, photograph, controls - rather than one
 * long scroll. Everything the user needs is on screen at once, and the photograph gets
 * whatever room is left over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    onRetake: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val sheetState = rememberModalBottomSheetState()

    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = "Your puzzle") {
            IconButton(onClick = onHistory) {
                Icon(Icons.Filled.DateRange, contentDescription = "History")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Square, and never larger than the space it has been given in either
            // direction - which `aspectRatio` alone does not guarantee.
            Box(
                modifier = Modifier
                    .size(minOf(maxWidth, maxHeight))
                    .pointerInput(state.grid, state.lines) {
                        detectTapGestures { offset ->
                            val column = state.lines.vertical
                                .indexOfLast { it * size.width <= offset.x }
                                .coerceIn(0, 8)
                            val row = state.lines.horizontal
                                .indexOfLast { it * size.height <= offset.y }
                                .coerceIn(0, 8)
                            onChange(state.copy(selectedCell = row * 9 + column))
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        drawOverlay(state, measurer)
                    },
            ) {
                Image(
                    bitmap = state.photo.asImageBitmap(),
                    contentDescription = "The puzzle you photographed",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Controls(state, onChange, onRetake)
    }

    state.selectedCell?.let { index ->
        ModalBottomSheet(
            onDismissRequest = { onChange(state.copy(selectedCell = null)) },
            sheetState = sheetState,
        ) {
            CellEditor(state, index, onChange)
        }
    }
}

@Composable
private fun Controls(
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.uncertainCells.isNotEmpty()) {
            ReadingBanner(state, onChange)
        }

        Text(
            state.status.text,
            style = MaterialTheme.typography.titleMedium,
            color = when (state.status.tone) {
                Tone.GOOD -> Overlays.correct
                Tone.BAD -> Overlays.incorrect
                Tone.NEUTRAL -> Color.Unspecified
            },
        )

        Legend(state.legend)

        state.guidance?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton("Hint", OverlayMode.HINT, state, onChange, Modifier.weight(1f), state.hint != null)
            ModeButton("Check", OverlayMode.CHECK, state, onChange, Modifier.weight(1f))
            ModeButton("Solve", OverlayMode.SOLUTION, state, onChange, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // Deliberately quieter than the three above: this answers "what did you
            // actually see?", which is a different question from "help me".
            TextButton(
                onClick = { onChange(state.show(OverlayMode.READING)) },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.overlay == OverlayMode.READING) "Hide the reading" else "What was read")
            }
            TextButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("New photo")
            }
        }
    }
}

/**
 * One of the three help modes. Selected modes are filled, so which one is on is visible
 * without reading; pressing the selected one turns it off again.
 */
@Composable
private fun ModeButton(
    label: String,
    mode: OverlayMode,
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    val onClick = { onChange(state.show(mode)) }
    if (state.overlay == mode) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
    }
}

/**
 * What the reader was unsure of, and the one action that settles it.
 *
 * This stays until every flagged cell has been either corrected or accepted, and then it
 * goes. The first version left the message on screen after the user had dealt with every
 * cell, which read as though the app had not noticed.
 */
@Composable
private fun ReadingBanner(state: PuzzleState, onChange: (PuzzleState) -> Unit) {
    val count = state.uncertainCells.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x22FFB300), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (count == 1) "The app is not sure about one square."
            else "The app is not sure about $count squares.",
            style = MaterialTheme.typography.titleSmall,
            color = Overlays.uncertain,
        )
        Text(
            "They are ringed on the photo. Tap one to fix it, or accept them all.",
            style = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(onClick = { onChange(state.acceptReading()) }) {
            Text("All correct")
        }
    }
}

/**
 * Correcting one cell.
 *
 * A modal sheet with a three-by-three keypad, which is the shape of the thing being
 * chosen. The first version put nine buttons and four more in flat rows at the bottom of
 * a scrolling page, where the last of them wrapped one letter per line.
 */
@Composable
private fun CellEditor(state: PuzzleState, index: Int, onChange: (PuzzleState) -> Unit) {
    val cell = state.grid[index]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Row ${index / 9 + 1}, column ${index % 9 + 1}",
            style = MaterialTheme.typography.titleMedium,
        )
        val report = state.reports?.getOrNull(index)
        Text(
            report?.describe() ?: when {
                !cell.isFilled -> "Read as empty."
                cell.source == CellSource.GIVEN -> "Read as a printed ${cell.digit}."
                else -> "Read as a handwritten ${cell.digit}."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        report?.secondGuess()?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        for (row in 0 until 3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (column in 0 until 3) {
                    val digit = row * 3 + column + 1
                    val chosen = cell.digit == digit
                    val press = {
                        val source = if (cell.source == CellSource.EMPTY) CellSource.GUESS else cell.source
                        onChange(state.withCell(index, digit, source).copy(selectedCell = null))
                    }
                    if (chosen) {
                        Button(onClick = press, modifier = Modifier.weight(1f)) { Text("$digit") }
                    } else {
                        OutlinedButton(onClick = press, modifier = Modifier.weight(1f)) { Text("$digit") }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    onChange(state.withCell(index, null, CellSource.EMPTY).copy(selectedCell = null))
                },
                modifier = Modifier.weight(1f),
            ) { Text("Empty") }

            if (cell.isFilled) {
                OutlinedButton(
                    onClick = {
                        val flipped = if (cell.source == CellSource.GIVEN) CellSource.GUESS else CellSource.GIVEN
                        onChange(state.withCell(index, cell.digit, flipped).copy(selectedCell = null))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (cell.source == CellSource.GIVEN) "Handwritten" else "Printed")
                }
            }
        }

        if (index in state.uncertainCells) {
            Button(
                onClick = {
                    onChange(
                        state.copy(
                            uncertainCells = state.uncertainCells - index,
                            selectedCell = null,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("That is right") }
        }
    }
}

/**
 * Draws the overlay in cell coordinates: the rectified photo is a square, so this is
 * just ninths.
 *
 * Fills carry meaning, and there is at most one per cell. Doubt is drawn as a ring
 * around the cell so it never mixes with a fill to make a colour that means nothing,
 * which is what the first version did.
 */
private fun DrawScope.drawOverlay(state: PuzzleState, measurer: TextMeasurer) {
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

    for ((index, digit) in state.overlayDigits()) {
        val colour = Overlays.colour(digit.role)
        when (digit.role) {
            // The paper already shows the right digit, so a tint is all that is needed.
            OverlayRole.CORRECT -> squares.fill(this, index, colour.copy(alpha = 0.32f))

            // Tint, and then what the app read, on a chip in the corner. On a chip
            // because it is the app talking, not a digit on the paper - without that, a
            // misreading is indistinguishable from the app marking a right answer wrong,
            // which is exactly how it was first reported.
            OverlayRole.INCORRECT -> {
                squares.fill(this, index, colour.copy(alpha = 0.42f))
                drawChip(measurer, squares, index, digit.digit, colour)
            }

            OverlayRole.SOLUTION, OverlayRole.HINT ->
                drawCentred(measurer, squares, index, digit.digit.toString(), colour)
        }
    }

    for (index in state.uncertainCells) {
        val at = squares.topLeft(index)
        val cell = squares.size(index)
        drawRect(
            color = Overlays.uncertain,
            topLeft = at + Offset(cell.width * 0.05f, cell.height * 0.05f),
            size = Size(cell.width * 0.90f, cell.height * 0.90f),
            style = Stroke(width = squares.unit * 0.07f),
        )
    }

    state.selectedCell?.let { index ->
        drawRect(Color.White, squares.topLeft(index), squares.size(index), style = Stroke(width = 4f))
    }
}

/**
 * What the reader made of every square, drawn over the photograph.
 *
 * A tint says which of the four things the square was taken to be, a chip repeats the
 * digit that was read, and a bar along the bottom says how sure the classifier was. It
 * exists because after a read there was otherwise no way to see what the app had decided
 * - only what it had decided to do about it.
 */
private fun DrawScope.drawReading(state: PuzzleState, measurer: TextMeasurer, squares: Squares) {
    val reports = state.reports
    for (index in 0 until 81) {
        val report = reports?.getOrNull(index)

        // Without reports - a puzzle reopened from history - fall back to the grid,
        // which still knows print from handwriting even though the numbers are gone.
        val ink = report?.ink ?: when (state.grid[index].source) {
            CellSource.GIVEN -> Ink.PRINTED
            CellSource.GUESS -> Ink.ANSWER
            CellSource.EMPTY -> Ink.NONE
        }
        val digit = report?.digit ?: state.grid[index].digit

        val tint = when (ink) {
            Ink.PRINTED -> Overlays.printed
            Ink.ANSWER -> Overlays.written
            Ink.MARK -> Overlays.marks
            Ink.NONE -> null
        }
        if (tint != null) {
            squares.fill(this, index, tint.copy(alpha = 0.30f))
        }
        if (digit != null && tint != null) {
            drawChip(measurer, squares, index, digit, tint)
            report?.let { drawConfidence(squares, index, it.confidence) }
        }
    }
}

/** How sure the classifier was, as a bar across the foot of the cell. */
private fun DrawScope.drawConfidence(squares: Squares, index: Int, confidence: Float) {
    val at = squares.topLeft(index)
    val cell = squares.size(index)
    val height = squares.unit * 0.07f
    val inset = squares.unit * 0.08f
    val width = cell.width - inset * 2
    val top = at.y + cell.height - height - inset * 0.5f
    drawRect(Color(0x55000000), Offset(at.x + inset, top), Size(width, height))
    drawRect(
        color = when {
            confidence >= 0.9f -> Overlays.correct
            confidence >= 0.6f -> Overlays.uncertain
            else -> Overlays.incorrect
        },
        topLeft = Offset(at.x + inset, top),
        size = Size(width * confidence.coerceIn(0f, 1f), height),
    )
}

/** The digit the app read, drawn as a label rather than as ink on the page. */
private fun DrawScope.drawChip(
    measurer: TextMeasurer,
    squares: Squares,
    index: Int,
    digit: Int,
    colour: Color,
) {
    val layout = measurer.measure(
        digit.toString(),
        style = TextStyle(
            color = Color.White,
            fontSize = (squares.unit * 0.40f / 2.2f).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    val origin = squares.topLeft(index)
    val cell = squares.size(index)
    val padding = squares.unit * 0.05f
    val width = layout.size.width + padding * 2
    val height = layout.size.height + padding
    val at = Offset(origin.x + cell.width - width - padding, origin.y + padding)
    drawRoundRect(
        color = colour.copy(alpha = 0.95f),
        topLeft = at,
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(squares.unit * 0.08f),
    )
    drawText(layout, topLeft = Offset(at.x + padding, at.y + padding / 2))
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

    fun size(index: Int) = Size(
        xs[index % 9 + 1] - xs[index % 9],
        ys[index / 9 + 1] - ys[index / 9],
    )

    fun fill(scope: DrawScope, index: Int, colour: Color) =
        scope.drawRect(colour, topLeft(index), size(index))
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

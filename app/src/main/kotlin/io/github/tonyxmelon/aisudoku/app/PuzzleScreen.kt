package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
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
import io.github.tonyxmelon.aisudoku.BuildConfig
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.recognize.Ink

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    onMenu: () -> Unit,
    onRetake: () -> Unit,
    onSettings: () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val sheetState = rememberModalBottomSheetState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val photoSide = (maxWidth - 24.dp).coerceAtMost(maxHeight * 0.52f)

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(
                title = "AI Sudoku",
                subtitle = BuildConfig.VERSION_NAME,
                onMenu = onMenu,
            ) {
                IconButton(onClick = onRetake) {
                    Icon(CameraIcon, contentDescription = "Take a new photo")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(photoSide)
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

            Controls(state, onChange, modifier = Modifier.weight(1f))
        }
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton("Hint", OverlayMode.HINT, state, onChange, Modifier.weight(1f), state.hint != null)
            ModeButton("Check", OverlayMode.CHECK, state, onChange, Modifier.weight(1f))
            ModeButton("Solve", OverlayMode.SOLUTION, state, onChange, Modifier.weight(1f))
            ModeButton("Read", OverlayMode.READING, state, onChange, Modifier.weight(1f))
        }

        Box(Modifier.size(8.dp))
    }
}

/**
 * One of the four layers. The selected one is filled, so which is on can be seen without
 * reading; pressing it again turns it off.
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
    // Four buttons across a phone leaves about fifty dip each, so the default padding has
    // to go or the labels wrap.
    val padding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    val text: @Composable () -> Unit = {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
    if (state.overlay == mode) {
        Button(onClick, modifier, enabled, contentPadding = padding) { text() }
    } else {
        OutlinedButton(onClick, modifier, enabled, contentPadding = padding) { text() }
    }
}

/**
 * What the reader was unsure of, and the one action that settles it.
 *
 * This stays until every flagged square has been either corrected or accepted, and then
 * it goes. The first version left the message on screen after the user had dealt with
 * every one, which read as though the app had not noticed.
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
            "Their bar is amber or red on the photo. Tap one to fix it, or accept them all.",
            style = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(onClick = { onChange(state.acceptReading()) }) {
            Text("All correct")
        }
    }
}

/**
 * Correcting one square.
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
                        val source =
                            if (cell.source == CellSource.EMPTY) CellSource.GUESS else cell.source
                        onChange(state.withCell(index, digit, source).copy(selectedCell = null))
                    }
                    if (chosen) {
                        Button(onClick = press, modifier = Modifier.weight(1f)) { Text("$digit") }
                    } else {
                        OutlinedButton(onClick = press, modifier = Modifier.weight(1f)) {
                            Text("$digit")
                        }
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
                        val flipped =
                            if (cell.source == CellSource.GIVEN) CellSource.GUESS else CellSource.GIVEN
                        onChange(
                            state.withCell(index, cell.digit, flipped).copy(selectedCell = null)
                        )
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
 * Draws the overlay over the photograph.
 *
 * Fills carry meaning, and there is at most one per square. Doubt is drawn as a ring
 * around the square so it never mixes with a fill to make a colour that means nothing,
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

            // Tint, and then what the app read, in the corner. Drawn exactly as the
            // reading layer draws it, because it is the same statement: this is what I
            // saw there. Without it a misreading is indistinguishable from the app
            // marking a right answer wrong, which is how it was first reported.
            OverlayRole.INCORRECT -> {
                squares.fill(this, index, colour.copy(alpha = 0.42f))
                drawReadDigit(measurer, squares, index, digit.digit)
            }

            OverlayRole.SOLUTION, OverlayRole.HINT ->
                drawCentred(measurer, squares, index, digit.digit.toString(), colour)
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
            reports.getOrNull(index)?.let { drawConfidence(squares, index, it.confidence) }
        }
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
 *  - the digit sits in the **bottom-right corner**, small, on a translucent backing
 *    rather than a solid chip. Candidate marks are written along the top of a square and
 *    the answer through the middle, so the bottom-right corner is the emptiest part of a
 *    real page. A solid chip at the top-right sat exactly on top of the marks;
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

        // Pencil marks are tinted more faintly: there are forty of them on a busy page
        // and they are the least interesting thing on it.
        val marks = ink == Ink.MARK
        squares.fill(this, index, colour.copy(alpha = if (marks) 0.20f else 0.30f))

        if (digit != null) drawReadDigit(measurer, squares, index, digit)
    }
}

/**
 * How sure the classifier was, as a hairline along the very bottom edge.
 *
 * Thin and full width, so it reads as a gauge rather than as another thing sitting on
 * the page.
 */
private fun DrawScope.drawConfidence(squares: Squares, index: Int, confidence: Float) {
    // Green, amber, red by how likely the classifier thought its answer was. A square the
    // reader flagged can never come out green: it is flagged on the gap between the top
    // two answers, and those sum with the rest to one, so a nine-tenths answer leaves a
    // gap too wide to flag.
    val at = squares.topLeft(index)
    val cell = squares.size(index)
    val height = squares.unit * 0.045f
    val inset = squares.unit * 0.06f
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

/**
 * The digit the app read, in the corner of the square.
 *
 * Small, in the bottom-right, on a translucent dark backing rather than a solid chip in
 * the app's own colour. Three things follow from wanting to compare it against the paper:
 * it has to be legible over anything, it must not cover the paper's own digit in the
 * middle, and it must not cover the candidate marks along the top. The backing is dark
 * rather than coloured because the tint of the square already says what kind of digit
 * this is; repeating that here only spends contrast the digit needs.
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
            color = Color.White,
            fontSize = (squares.unit * 0.34f / 2.2f).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    val origin = squares.topLeft(index)
    val cell = squares.size(index)
    val padding = squares.unit * 0.045f
    val width = layout.size.width + padding * 2
    val height = layout.size.height + padding
    val at = Offset(
        origin.x + cell.width - width - padding,
        origin.y + cell.height - height - squares.unit * 0.13f,
    )
    drawRoundRect(
        color = Color(0xAA101010),
        topLeft = at,
        size = Size(width, height),
        cornerRadius = CornerRadius(squares.unit * 0.06f),
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

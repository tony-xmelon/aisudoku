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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tonyxmelon.aisudoku.BuildConfig
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.recognize.Ink
import io.github.tonyxmelon.aisudoku.solver.Techniques

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
    onAbout: () -> Unit,
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
                IconButton(onClick = onAbout) {
                    Icon(Icons.Filled.Info, contentDescription = "About")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
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

                // How many squares are still empty, in the corner under the grid it
                // counts. It was a sentence in the pane below, where it was the only
                // thing said most of the time and cost a line that the reasoning needed.
                Text(
                    state.progress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(photoSide).padding(top = 2.dp),
                )
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
    Column(modifier = modifier.fillMaxWidth()) {
        // Everything whose height depends on what is being said goes above the buttons and
        // scrolls in its own space. A banner appearing used to push the buttons down the
        // screen, out from under the thumb that was about to press one.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.uncertainCells.isNotEmpty()) {
                ReadingBanner(state, onChange)
            }

            // Only when there is something to say. Silence is the ordinary case.
            state.status?.let { status ->
                Text(
                    status.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = when (status.tone) {
                        Tone.GOOD -> Overlays.correct
                        Tone.BAD -> Overlays.incorrect
                        Tone.NEUTRAL -> Color.Unspecified
                    },
                )
            }

            // The key names the technique, so the pane does not have to.
            Legend(state.legend, evidenceLabel = state.evidenceLabel)

            state.guidance?.let { guidance ->
                Text(guidance.body, style = MaterialTheme.typography.bodyMedium)

                guidance.howTo?.let { howTo ->
                    HowTo(howTo, technique = state.evidenceLabel)
                }

                // Last, because it is the summary of a move whose reasoning is above it.
                guidance.effect?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // The tutor's opening remark, and only that: on the first step of its own
            // route, once the tutor has been started. It was being reprinted under every
            // step of the walk, where a paragraph about the route as a whole was pushing
            // the reasoning for the step in front of you off the bottom of the pane.
            if (state.overlay == OverlayMode.LESSON &&
                state.tutorTechnique == null &&
                state.lessonStep == 0
            ) {
                state.outlook?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Pinned, so they are in the same place whatever is being said above them. One
        // block with one lot of system-bar inset: both rows used to apply their own, which
        // left a bar of dead screen between them that the pane above could have used.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WalkthroughRow(state, onChange)

            // Read, Check, Hint, Solve: the order you would use them in. Read is what to
            // press first, when the question is still whether the app got the puzzle
            // right, and Solve is the one that ends the exercise.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeButton("Read", OverlayMode.READING, state, onChange, Modifier.weight(1f))
                ModeButton("Check", OverlayMode.CHECK, state, onChange, Modifier.weight(1f))
                ModeButton("Hint", OverlayMode.HINT, state, onChange, Modifier.weight(1f), state.hint != null)
                ModeButton("Solve", OverlayMode.SOLUTION, state, onChange, Modifier.weight(1f))
            }
        }
    }
}

/**
 * The long "how to hunt for one of these" text, folded away until asked for.
 *
 * It runs to paragraphs, it is the same words every time that technique comes round, and
 * printed in full it pushed this position's own reasoning off the bottom of the pane. It
 * stays open while the tutor walks steps of the same technique and closes when the
 * technique changes, which is when it would have become the wrong text.
 */
@Composable
private fun HowTo(text: String, technique: String?) {
    var open by remember(technique) { mutableStateOf(false) }
    TextButton(
        onClick = { open = !open },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            if (open) "Hide how to spot one" else "How to spot one",
            style = MaterialTheme.typography.labelMedium,
        )
    }
    if (open) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The way into the walkthrough, and the way through it.
 *
 * A separate row from the four layers because it is a different kind of thing: those
 * answer "show me something about this puzzle", this one answers "teach me how to finish
 * it". It is also the only control here that changes what it is, which is a good reason
 * to keep it away from the ones that must not move.
 */
@Composable
private fun WalkthroughRow(state: PuzzleState, onChange: (PuzzleState) -> Unit) {
    val route = state.walkthrough ?: return
    if (route.isEmpty) return

    if (state.overlay != OverlayMode.LESSON) {
        Button(onClick = { onChange(state.tutor()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Start Tutor")
        }
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = { onChange(state.stepTo(state.lessonStep - 1)) },
            enabled = state.lessonStep > 0,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) { Text("Back", maxLines = 1) }

        TutorPicker(state, onChange, route.steps.size, Modifier.weight(1f))

        if (state.lessonStep < route.steps.size - 1) {
            Button(
                onClick = { onChange(state.stepTo(state.lessonStep + 1)) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) { Text("Next", maxLines = 1) }
        } else {
            Button(
                onClick = { onChange(state.copy(tutorTechnique = null).show(OverlayMode.LESSON)) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) { Text("Done", maxLines = 1) }
        }
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
 * Which list the tutor is walking, and a way to change it.
 *
 * The route is what the app would do next and is the right default, but on a hard puzzle
 * it can be six eliminations in a row - which reads as a list of unrelated facts unless
 * the user can see what else is on offer and go and look at it.
 *
 * Every technique is listed with the number of places it applies right now, including the
 * ones that apply nowhere: "naked single, none" is worth knowing when you are hunting for
 * one.
 */
@Composable
private fun TutorPicker(
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    steps: Int,
    modifier: Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val counts = state.findingCounts

    Box(modifier = modifier) {
        TextButton(
            onClick = { open = true },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${state.lessonStep + 1} of $steps",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.tutorTechnique ?: "Best route",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "Choose what to be shown",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Best route") },
                trailingIcon = { Text("$steps") },
                onClick = {
                    open = false
                    onChange(state.tutor())
                },
            )
            for (technique in Techniques.all) {
                val found = counts[technique.name] ?: 0
                DropdownMenuItem(
                    enabled = found > 0,
                    text = { Text(technique.name) },
                    trailingIcon = { Text(if (found > 0) "$found" else "none") },
                    onClick = {
                        open = false
                        onChange(state.tutor(technique.name))
                    },
                )
            }
        }
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
        // The reader's own reason, when it has one. "One cell looked like a printed
        // digit but is not" says far more than a count does, and it was being thrown away.
        state.readingNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Look for the thick amber bar on the photo. Tap that square to fix it, or " +
                "accept the reading as it stands.",
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
            // The sheet insets its top but not its bottom, so its last button was landing
            // underneath the system navigation buttons.
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Row ${index / 9 + 1}, column ${index % 9 + 1}",
            style = MaterialTheme.typography.titleMedium,
        )
        val report = state.reports?.getOrNull(index)
        val corrected = state.reports != null && report == null
        Text(
            when {
                report != null -> report.describe()
                corrected -> when {
                    !cell.isFilled -> "You cleared this square."
                    cell.source == CellSource.GIVEN -> "You set this to a printed ${cell.digit}."
                    else -> "You set this to a handwritten ${cell.digit}."
                }

                !cell.isFilled -> "Empty."
                cell.source == CellSource.GIVEN -> "A printed ${cell.digit}."
                else -> "A handwritten ${cell.digit}."
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

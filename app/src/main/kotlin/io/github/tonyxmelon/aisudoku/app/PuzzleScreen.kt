package io.github.tonyxmelon.aisudoku.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import io.github.tonyxmelon.aisudoku.solver.Walkthrough

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
    onStrategies: () -> Unit,
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
                OverflowMenu(onStrategies, onSettings, onAbout)
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

            // Everything below the photograph. The tutor rises over the whole of it,
            // buttons included, and the grid above never moves - which is the reason the
            // photograph takes its size from the window rather than from what is left.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Controls(state, onChange, modifier = Modifier.fillMaxSize())
                TutorSheet(state, onChange)
            }
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

            // A lesson says all of this in the sheet instead. The sheet is only as tall as
            // it needs to be, so anything left here would show above it - the key twice
            // over, which is how it first looked.
            if (state.overlay != OverlayMode.LESSON) {
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

                Lesson(state, showOutlook = false)
            }
        }

        // Pinned, so they are in the same place whatever is being said above them. Read,
        // Check, Hint, Solve is the order you would use them in: Read while the question
        // is still whether the app got the puzzle right, Solve when you have given up.
        val route = state.walkthrough?.takeIf { !it.isEmpty }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                // The tutor's lip sits below this row and carries the system-bar inset.
                // With no route to tutor - a finished puzzle, or one that would not read -
                // there is no lip, and this row is what would land under the system buttons.
                .then(if (route == null) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            ModeButton("Read", OverlayMode.READING, state, onChange, Modifier.weight(1f))
            ModeButton("Check", OverlayMode.CHECK, state, onChange, Modifier.weight(1f))
            ModeButton("Hint", OverlayMode.HINT, state, onChange, Modifier.weight(1f), state.hint != null)
            ModeButton("Solve", OverlayMode.SOLUTION, state, onChange, Modifier.weight(1f))
        }

        route?.let { TutorCollapsed(state, it, onChange) }
    }
}

/**
 * The tutor with only its lip showing, at the very bottom of the screen.
 *
 * It is the same panel as the open one, resting at the foot instead of a button that
 * opens it - so what it does is legible before you touch it, and the gesture that opens
 * it is the one that closes it again. Drag it up, flick it up, or tap it.
 */
@Composable
private fun TutorCollapsed(
    state: PuzzleState,
    route: Walkthrough,
    onChange: (PuzzleState) -> Unit,
) {
    val openAt = with(LocalDensity.current) { 28.dp.toPx() }
    var lifted by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = lifted }
            .clickable { onChange(state.tutor()) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    lifted = (lifted + delta).coerceAtMost(0f)
                },
                onDragStopped = { velocity ->
                    if (-lifted > openAt || velocity < -700f) onChange(state.tutor())
                    lifted = 0f
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Handle()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("Tutor", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(
                    "${route.steps.size} steps to the end",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The grab bar. The same one whether the panel is resting or open. */
@Composable
private fun Handle() {
    Box(
        Modifier
            .size(width = 36.dp, height = 4.dp)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(2.dp),
            )
    )
}

/**
 * What a step of the route says, wherever it is being said.
 *
 * Shared because the sheet and the pane want the same words in the same order: what is
 * true of this position, then the technique's how-to folded away, then what the move
 * actually does.
 */
@Composable
private fun ColumnScope.Lesson(state: PuzzleState, showOutlook: Boolean) {
    state.guidance?.let { guidance ->
        Text(guidance.body, style = MaterialTheme.typography.bodyMedium)

        // Last, because it is the summary of a move whose reasoning is above it.
        guidance.effect?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // The tutor's opening remark, and only that: on the first step of its own route. It
    // was being reprinted under every step, where a paragraph about the route as a whole
    // pushed the reasoning for the step in front of you off the bottom.
    if (showOutlook && state.tutorTechnique == null && state.lessonStep == 0) {
        state.outlook?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The tutor, as a sheet you pull up and push back down.
 *
 * It used to be a row of buttons that was always there and could only be left by pressing
 * Next until the route ran out - sixty presses on a hard puzzle, with Done at the end. A
 * sheet answers that with the gesture everyone already has: swipe it away. It covers the
 * layer buttons on purpose, because while it is up it is what the screen is for, and it
 * stops short of the photograph, which never moves.
 */
@Composable
private fun BoxScope.TutorSheet(state: PuzzleState, onChange: (PuzzleState) -> Unit) {
    val route = state.walkthrough
    val showing = state.overlay == OverlayMode.LESSON && route != null && !route.isEmpty

    AnimatedVisibility(
        visible = showing,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        // The whole controls area, always. An open panel that grew and shrank with the
        // length of the step under it moved everything on screen every time you stepped.
        modifier = Modifier.fillMaxSize(),
    ) {
        if (route != null && !route.isEmpty) TutorSheetContent(state, route, onChange)
    }
}

@Composable
private fun TutorSheetContent(
    state: PuzzleState,
    route: Walkthrough,
    onChange: (PuzzleState) -> Unit,
) {
    val last = route.steps.size - 1
    val at = state.lessonStep.coerceIn(0, last)
    val leaveAt = with(LocalDensity.current) { 72.dp.toPx() }
    val stepAt = with(LocalDensity.current) { 48.dp.toPx() }

    // How far the sheet has been dragged down, so it follows the finger before it goes.
    var pulled by remember { mutableFloatStateOf(0f) }

    // Whether the how-to is showing. Closes itself when the technique changes, which is
    // the moment it would have become the wrong text.
    var asking by remember(state.evidenceLabel) { mutableStateOf(false) }

    fun stepBy(by: Int) {
        val to = at + by
        if (to in 0..last) onChange(state.stepTo(to))
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxSize().graphicsLayer { translationY = pulled },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The handle, and the way out. Dragging it down past a threshold - or flicking
            // it - closes the tutor, which is the one thing the old row could not do.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            pulled = (pulled + delta).coerceAtLeast(0f)
                        },
                        onDragStopped = { velocity ->
                            if (pulled > leaveAt || velocity > 900f) {
                                onChange(state.close())
                            } else {
                                pulled = 0f
                            }
                        },
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Handle()
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TutorPicker(state, onChange, route.steps.size, Modifier.weight(1f))

                // Swiping is quick and coarse; these are for landing on one step exactly.
                IconButton(onClick = { stepBy(-1) }, enabled = at > 0) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous step")
                }
                Text(
                    "${at + 1} / ${route.steps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                IconButton(onClick = { stepBy(1) }, enabled = at < last) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next step")
                }

                // The technique's how-to, which is paragraphs long and the same words
                // every time that technique comes round. It had a line of its own saying
                // "How to spot one"; a question mark says it in no lines at all.
                // A question mark drawn as text: the core icon set has no help glyph, and
                // the extended set is tens of megabytes for one.
                IconButton(onClick = { asking = !asking }) {
                    Text(
                        "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (asking) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
            }

            ChapterStrip(state.chapters, at) { onChange(state.stepTo(it)) }

            Legend(state.legend, evidenceLabel = state.evidenceLabel)

            // Sideways for the next step, so the common move needs no button at all.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .pointerInput(at, last) {
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                when {
                                    dragged < -stepAt -> stepBy(1)
                                    dragged > stepAt -> stepBy(-1)
                                }
                            },
                        ) { _, amount -> dragged += amount }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Lesson(state, showOutlook = true)

                if (asking) {
                    state.guidance?.howTo?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The whole route as one line, a block per run of the same technique.
 *
 * Sixty steps drawn one mark each comes out finer than a fingertip and says nothing about
 * what the marks are. A dozen blocks can be hit, and the puzzle's shape shows in them: a
 * wide block is a long grind of one technique, a narrow one is a move that only worked
 * once. Tapping a block jumps to where that run begins.
 */
@Composable
private fun ChapterStrip(chapters: List<Chapter>, at: Int, onJump: (Int) -> Unit) {
    if (chapters.isEmpty()) return
    val here = chapters.firstOrNull { at in it.from until it.until }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().height(24.dp),
        ) {
            for (chapter in chapters) {
                val colour = when {
                    chapter === here -> MaterialTheme.colorScheme.primary
                    chapter.until <= at -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                }
                Box(
                    modifier = Modifier
                        .weight(chapter.count.toFloat())
                        .fillMaxHeight()
                        .clickable { onJump(chapter.from) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colour)
                    )
                }
            }
        }
        here?.let {
            Text(
                if (it.count == 1) it.technique
                else "${it.technique} - step ${at - it.from + 1} of ${it.count}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
) = Pill(label, state.overlay == mode, enabled, modifier) { onChange(state.show(mode)) }

/**
 * One of the five. The selected one is filled, so which is on can be seen without reading.
 *
 * Five across a phone leaves about forty-five dip each, so the default padding has to go
 * or the labels wrap.
 */
@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val padding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
    val text: @Composable () -> Unit = {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
    if (selected) {
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
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.tutorTechnique ?: "Best route",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Choose what to be shown",
                    modifier = Modifier.size(18.dp),
                )
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

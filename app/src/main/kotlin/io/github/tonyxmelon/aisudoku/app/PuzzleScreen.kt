package io.github.tonyxmelon.aisudoku.app

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import io.github.tonyxmelon.aisudoku.BuildConfig
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.solver.Chain
import io.github.tonyxmelon.aisudoku.recognize.Ink
import io.github.tonyxmelon.aisudoku.solver.Techniques
import io.github.tonyxmelon.aisudoku.solver.Walkthrough

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
}

/** One arrow, stopping short at both ends so it does not run over the digits it joins. */
private fun DrawScope.drawArrow(from: Offset, to: Offset, unit: Float) {
    val step = to - from
    val length = hypot(step.x, step.y)
    if (length < 1f) return

    val direction = Offset(step.x / length, step.y / length)
    val clear = unit * 0.34f
    if (length <= clear * 2f + unit * 0.1f) return

    val start = from + direction * clear
    val end = to - direction * clear
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

            // Everything below the photograph. The tutor rests across the foot of it and
            // grows to fill the whole of it, buttons included, while the grid above never
            // moves - which is the reason the photograph takes its size from the window
            // rather than from what is left over.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val route = state.walkthrough?.takeIf { !it.isEmpty }
                val peek = TUTOR_PEEK +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                Controls(
                    state,
                    onChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (route != null) peek else 0.dp),
                )
                route?.let { TutorPanel(state, it, onChange, peek, maxHeight) }
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                // The tutor rests below this row and carries the system-bar inset for it.
                // With no route to tutor - a finished puzzle, or one that would not read -
                // there is no panel, and this row is what would land under the system
                // buttons instead.
                .then(
                    if (state.walkthrough?.takeIf { !it.isEmpty } == null) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            ModeButton("Read", OverlayMode.READING, state, onChange, Modifier.weight(1f))
            ModeButton("Check", OverlayMode.CHECK, state, onChange, Modifier.weight(1f))
            ModeButton("Hint", OverlayMode.HINT, state, onChange, Modifier.weight(1f), state.hint != null)
            ModeButton("Solve", OverlayMode.SOLUTION, state, onChange, Modifier.weight(1f))
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
 * The tutor: one panel, resting across the foot of the screen or filling it.
 *
 * It was two things before - a labelled band at the bottom, and a separate sheet that
 * appeared once the band had been dragged far enough. Dragging the band therefore moved
 * the band, and the panel arrived afterwards, which is not the same gesture at all. This
 * is one Surface whose height changes: what you drag is the thing that grows, and letting
 * go settles it to whichever end it is nearer.
 *
 * Resting, only its title shows. Everything below the title is laid out as usual and
 * simply clipped away, so the open panel is the same panel and not a second one.
 */
@Composable
private fun BoxScope.TutorPanel(
    state: PuzzleState,
    route: Walkthrough,
    onChange: (PuzzleState) -> Unit,
    peek: Dp,
    full: Dp,
) {
    val density = LocalDensity.current
    val peekPx = with(density) { peek.toPx() }
    val fullPx = with(density) { full.toPx() }
    val open = state.overlay == OverlayMode.LESSON

    // Null except while a finger is on it. The animation owns the height the rest of the
    // time, so a step that changes the text cannot jog the panel.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val settled by animateFloatAsState(if (open) fullPx else peekPx, label = "tutor height")
    val heightPx = (dragged ?: settled).coerceIn(peekPx, fullPx)

    val last = route.steps.size - 1
    val at = state.lessonStep.coerceIn(0, last)
    val stepAt = with(density) { 48.dp.toPx() }

    // Whether the how-to is showing. Closes itself when the technique changes, which is
    // the moment it would have become the wrong text.
    var asking by remember(state.evidenceLabel) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val bar = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    fun stepBy(by: Int) {
        val to = at + by
        if (to in 0..last) onChange(state.stepTo(to))
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(with(density) { heightPx.toDp() })
            .clipToBounds(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The grip: the handle and the title under it. Drag either way, or tap to
            // open and tap again to shut. It is the only part of the panel that is always
            // on screen, so it is the only part that can be the way in or out.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            dragged = ((dragged ?: heightPx) - delta).coerceIn(peekPx, fullPx)
                        },
                        onDragStopped = { velocity ->
                            val height = dragged ?: heightPx
                            val wanted = when {
                                velocity < -600f -> true
                                velocity > 600f -> false
                                else -> height > (peekPx + fullPx) / 2f
                            }
                            dragged = null
                            if (wanted != open) {
                                onChange(if (wanted) state.tutor() else state.close())
                            }
                        },
                    )
                    .clickable { onChange(if (open) state.close() else state.tutor()) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(modifier = Modifier.padding(top = 8.dp)) { Handle() }

                // The name on the left where a title belongs, and the stepping centred,
                // because it is the control your thumb goes to and the middle is where a
                // thumb lands. A Box rather than a Row so the middle is the middle of the
                // panel and not of whatever is left over beside the title.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Tutor",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )

                    if (open) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.Center),
                        ) {
                            // Swiping is quick and coarse; these land on one step exactly.
                            IconButton(onClick = { stepBy(-1) }, enabled = at > 0) {
                                Icon(
                                    Icons.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous step",
                                )
                            }
                            Text(
                                "${at + 1} / ${route.steps.size}",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                            IconButton(onClick = { stepBy(1) }, enabled = at < last) {
                                Icon(
                                    Icons.Filled.KeyboardArrowRight,
                                    contentDescription = "Next step",
                                )
                            }
                        }
                    }
                }
            }

            // Nothing below the grip is worth building while the panel is shut: the route,
            // its chapters and the step's own text all cost a solve, and none of it can be
            // seen.
            if (heightPx > peekPx + 1f) {
                // One line for what is being walked, what the colours mean, and how far
                // through this run of the technique you are. The technique's name was
                // being printed twice - once here and once in the key beside the colour of
                // its own squares - and the key is the one that earns it.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TutorPicker(state, onChange, route.steps.size, Modifier)
                    Legend(
                        state.legend,
                        modifier = Modifier.weight(1f),
                        evidenceLabel = state.evidenceLabel,
                    )
                    ChapterCount(state.chapters, at)
                }

                ChapterStrip(state.chapters, at) { onChange(state.stepTo(it)) }

                // Sideways for the next step, so the common move needs no button at all.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        // A thread of a scrollbar, so it is visible that there is more
                        // below without anything being spent on saying so.
                        .drawWithContent {
                            drawContent()
                            if (scroll.maxValue > 0) {
                                val track = size.height
                                val thumb = (track * track / (track + scroll.maxValue))
                                    .coerceAtLeast(24.dp.toPx())
                                val width = 3.dp.toPx()
                                drawRoundRect(
                                    color = bar,
                                    topLeft = Offset(
                                        size.width - width,
                                        (track - thumb) *
                                            (scroll.value.toFloat() / scroll.maxValue),
                                    ),
                                    size = Size(width, thumb),
                                    cornerRadius = CornerRadius(width / 2f),
                                )
                            }
                        }
                        .verticalScroll(scroll)
                        .pointerInput(at, last) {
                            var swiped = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { swiped = 0f },
                                onDragEnd = {
                                    when {
                                        swiped < -stepAt -> stepBy(1)
                                        swiped > stepAt -> stepBy(-1)
                                    }
                                },
                            ) { _, amount -> swiped += amount }
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Lesson(state, showOutlook = true)

                    state.guidance?.howTo?.let {
                        HowTo(it, asking) { asking = !asking }
                    }
                }
            }
        }
    }
}

/**
 * The technique's how-to, under the step it belongs to.
 *
 * Paragraphs long and the same words every time that technique comes round, so it is not
 * printed until it is asked for. It sits at the end of the step rather than in the
 * panel's header, where a question mark beside the stepping controls looked like help
 * with the controls.
 */
@Composable
private fun HowTo(text: String, open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "how")

    Row(
        modifier = Modifier.clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "how",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = if (open) "Hide how to spot one" else "How to spot one",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
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

/** How much of the tutor shows when it is shut: its handle and its title. */
private val TUTOR_PEEK = 56.dp

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
}

/**
 * How far through this run of the technique you are.
 *
 * The technique itself is not named here. It is named in the key, beside the colour of the
 * squares it is talking about, which is the one place it earns its width.
 */
@Composable
private fun ChapterCount(chapters: List<Chapter>, at: Int) {
    val here = chapters.firstOrNull { at in it.from until it.until } ?: return
    if (here.count == 1) return
    Text(
        "${at - here.from + 1} of ${here.count}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
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

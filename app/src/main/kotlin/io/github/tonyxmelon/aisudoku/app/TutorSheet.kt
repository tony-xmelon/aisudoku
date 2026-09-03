package io.github.tonyxmelon.aisudoku.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tonyxmelon.aisudoku.solver.Walkthrough
import kotlinx.coroutines.launch
/*
 * The tutor: a panel that pulls up from the bottom over the layer buttons.
 *
 * Split out of PuzzleScreen. It is the largest single thing the screen does and the only
 * part with a gesture and an animation of its own.
 */
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
internal fun ColumnScope.Lesson(state: PuzzleState, trailing: @Composable () -> Unit = {}) {
    val guidance = state.guidance ?: return

    if (guidance.effect == null) {
        Trailing(guidance.body, MaterialTheme.typography.bodyMedium, null, trailing)
    } else {
        Text(guidance.body, style = MaterialTheme.typography.bodyMedium)
        // Last, because it is the summary of a move whose reasoning is above it.
        Trailing(
            guidance.effect,
            MaterialTheme.typography.bodySmall,
            MaterialTheme.colorScheme.onSurfaceVariant,
            trailing,
        )
    }
}

/**
 * A paragraph with a small control after its last word rather than under it.
 *
 * The text takes only the width it needs, so a short paragraph leaves the control beside
 * it and a long one leaves it beside the last line. Aligned to the bottom, which is what
 * puts it on that last line rather than the first.
 */
@Composable
private fun Trailing(
    text: String,
    style: TextStyle,
    colour: Color?,
    trailing: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text,
            style = style,
            color = colour ?: Color.Unspecified,
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing()
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
internal fun BoxScope.TutorPanel(
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
    val scope = rememberCoroutineScope()

    // One value for the height, which the finger writes to directly and an animation
    // settles afterwards. It used to be an animateFloatAsState racing a separate drag
    // offset: opening began the animation, the animation finished while the finger was
    // still down, and letting go handed control back to a value that had long since
    // reached the top - so the panel jumped, and the whole opening played again.
    val height = remember { Animatable(peekPx) }
    var dragging by remember { mutableStateOf(false) }

    // The only thing that settles the panel, so it can only ever come to rest open or
    // shut. Letting the drag handler animate as well left it stopped halfway whenever the
    // two disagreed about which of them was finishing the job.
    LaunchedEffect(open, dragging, peekPx, fullPx) {
        if (!dragging) height.animateTo(if (open) fullPx else peekPx)
    }

    val heightPx = height.value
    val last = PuzzleLogic.lastStep(route)
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
            // Tight at the top, because everything up there is a label or a control and
            // the room it takes comes straight out of the explanation underneath.
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // The grip: the handle and the title under it. Drag either way, or tap to
            // open and tap again to shut. It is the only part of the panel that is always
            // on screen, so it is the only part that can be the way in or out.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        // Opened the moment the drag begins, not when it is let go, so that
                        // what is dragged into view is what stays there. Waiting until the
                        // release meant the panel showed nothing while being pulled and its
                        // first page once let go, which changed under the reader exactly as
                        // the movement ended.
                        onDragStarted = {
                            dragging = true
                            if (!open) onChange(state.reopenTutor())
                        },
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                height.snapTo((height.value - delta).coerceIn(peekPx, fullPx))
                            }
                        },
                        onDragStopped = { velocity ->
                            val wanted = when {
                                velocity < -600f -> true
                                velocity > 600f -> false
                                else -> height.value > (peekPx + fullPx) / 2f
                            }
                            if (wanted != open) {
                                onChange(if (wanted) state.reopenTutor() else state.close())
                            }
                            dragging = false
                        },
                    )
                    .clickable { onChange(if (open) state.close() else state.reopenTutor()) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(modifier = Modifier.padding(top = 6.dp)) { Handle() }

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
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous step",
                                )
                            }
                            Text(
                                "$at / ${route.steps.size}",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                            IconButton(onClick = { stepBy(1) }, enabled = at < last) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                    TutorPicker(state, onChange, state.routeLength, Modifier)
                    Legend(
                        state.legend,
                        modifier = Modifier.weight(1f),
                        evidenceLabel = state.evidenceLabel,
                    )
                    ChapterCount(state.chapters, at - 1)
                }

                // Minus one, because the strip is a picture of the route and the route
                // starts at step one; step zero is the tutor talking about it.
                ChapterStrip(state.chapters, at - 1) { onChange(state.stepTo(it + 1)) }

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
                    Spacer(Modifier.height(2.dp))
                    Lesson(state) {
                        state.guidance?.howTo?.let { HowTo(asking) { asking = !asking } }
                    }

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
private fun HowTo(open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "how")

    Row(
        modifier = Modifier.clickable(onClick = onToggle).padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "how",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (open) "Hide how to spot one" else "How to spot one",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
        )
    }
}

/** How much of the tutor shows when it is shut: its handle and its title. */
internal val TUTOR_PEEK = 56.dp

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
        modifier = Modifier.fillMaxWidth().height(18.dp),
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
                        .height(8.dp)
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

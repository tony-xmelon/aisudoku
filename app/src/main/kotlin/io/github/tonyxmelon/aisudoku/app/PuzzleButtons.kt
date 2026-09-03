package io.github.tonyxmelon.aisudoku.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
/*
 * The small controls the puzzle screen is built from.
 *
 * Split out of PuzzleScreen: shared shapes rather than a feature, which is why they are
 * worth having somewhere a person would look for them.
 */
@Composable
internal fun ModeButton(
    label: String,
    mode: OverlayMode,
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) = Pill(label, state.overlay == mode, enabled, modifier) { onChange(state.show(mode)) }

/**
 * Hint, with how far down its staircase you are drawn into it.
 *
 * A hint has four treads - the box, the technique, the square, the digit - and the button
 * gave no sign of that, so a press that revealed the next tread looked like a press that
 * had done nothing. It fills a quarter at a time instead, which says both that there is
 * more and how much more.
 */
@Composable
internal fun HintButton(
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    modifier: Modifier,
) {
    val showing = state.overlay == OverlayMode.HINT
    val rungs = if (state.hintStyle == HintStyle.EXPLAIN) PuzzleLogic.HINT_DEPTHS else 1
    val filled = if (showing) (state.hintDepth + 1f) / rungs else 0f
    val poured by animateFloatAsState(filled.coerceIn(0f, 1f), label = "hint")
    val colour = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    // Behind the button rather than in it, so the label stays the one colour throughout
    // and stays readable over both the filled part and the empty part.
    Pill(
        "Hint",
        selected = false,
        enabled = state.hint != null,
        modifier = modifier
            .clip(ButtonDefaults.outlinedShape)
            .drawBehind {
                if (poured > 0f) drawRect(colour, size = Size(size.width * poured, size.height))
            },
    ) {
        onChange(state.show(OverlayMode.HINT))
    }
}

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

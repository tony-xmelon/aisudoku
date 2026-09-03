package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.tonyxmelon.aisudoku.BuildConfig
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

                Lesson(state)
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
            HintButton(state, onChange, Modifier.weight(1f))
            ModeButton("Solve", OverlayMode.SOLUTION, state, onChange, Modifier.weight(1f))
        }
    }
}

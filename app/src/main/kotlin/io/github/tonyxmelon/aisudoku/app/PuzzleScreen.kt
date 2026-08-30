package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.solver.Hint
import io.github.tonyxmelon.aisudoku.solver.SolveResult

/**
 * The straightened photograph with help drawn on top.
 *
 * The working surface is the rectified image rather than the original: it is the same
 * photograph, and a square grid makes the overlay a matter of dividing by nine.
 */
@Composable
fun PuzzleScreen(
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    onRetake: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
) {
    val measurer = rememberTextMeasurer()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(state.grid) {
                        detectTapGestures { offset ->
                            val cellSize = size.width / 9f
                            val column = (offset.x / cellSize).toInt().coerceIn(0, 8)
                            val row = (offset.y / cellSize).toInt().coerceIn(0, 8)
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }

        item { StatusLine(state) }
        item { ActionRow(state, onChange, onRetake, onSettings, onHistory) }
        state.selectedCell?.let { index -> item { CellEditor(state, index, onChange) } }
    }
}

@Composable
private fun StatusLine(state: PuzzleState) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        // Only while cells are actually outstanding. Previously this string stayed on
        // screen after every one had been confirmed.
        if (state.uncertainCells.isNotEmpty()) {
            state.message?.let {
                Text(it, color = Color(0xFFFFD54F), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            state.status,
            color = if (state.solve is SolveResult.Unique) Color.Unspecified else Color(0xFFFF8A80),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.overlay == OverlayMode.HINT) {
            when (val hint = state.hint) {
                is Hint.Explained -> Text(
                    "${hint.technique}: ${hint.explanation}",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                is Hint.Reveal -> Text(
                    "Try row ${hint.index / 9 + 1}, column ${hint.index % 9 + 1}.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                null -> Text("Nothing left to hint at.", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    onRetake: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.overlay == OverlayMode.HINT,
                onClick = {
                    onChange(
                        state.copy(
                            overlay = if (state.overlay == OverlayMode.HINT) OverlayMode.NONE else OverlayMode.HINT,
                            revealedHintDigit = false,
                        )
                    )
                },
                label = { Text("Hint") },
            )
            FilterChip(
                selected = state.overlay == OverlayMode.CHECK,
                onClick = {
                    onChange(state.copy(overlay = if (state.overlay == OverlayMode.CHECK) OverlayMode.NONE else OverlayMode.CHECK))
                },
                label = { Text("Check mine") },
            )
            FilterChip(
                selected = state.overlay == OverlayMode.SOLUTION,
                onClick = {
                    onChange(state.copy(overlay = if (state.overlay == OverlayMode.SOLUTION) OverlayMode.NONE else OverlayMode.SOLUTION))
                },
                label = { Text("Solve") },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.overlay == OverlayMode.HINT && state.hintStyle == HintStyle.EXPLAIN && !state.revealedHintDigit) {
                TextButton(onClick = { onChange(state.copy(revealedHintDigit = true)) }) {
                    Text("Show me the digit")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRetake) { Text("Take another photo") }
            NavigationRow(onHistory = onHistory, onSettings = onSettings)
        }
    }
}

@Composable
private fun CellEditor(state: PuzzleState, index: Int, onChange: (PuzzleState) -> Unit) {
    val cell = state.grid[index]
    Column(
        modifier = Modifier.padding(top = 12.dp).background(Color(0x22FFFFFF)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Row ${index / 9 + 1}, column ${index % 9 + 1} - read as " +
                (cell.digit?.toString() ?: "empty") +
                if (cell.isFilled) " (${if (cell.source == CellSource.GIVEN) "printed" else "handwritten"})" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..9).forEach { digit ->
                TextButton(onClick = {
                    val source = if (cell.source == CellSource.EMPTY) CellSource.GUESS else cell.source
                    onChange(state.withCell(index, digit, source).copy(selectedCell = null))
                }) { Text("$digit") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (index in state.uncertainCells) {
                TextButton(onClick = {
                    onChange(state.copy(uncertainCells = state.uncertainCells - index, selectedCell = null))
                }) { Text("Looks right") }
            }

            TextButton(onClick = {
                onChange(state.withCell(index, null, CellSource.EMPTY).copy(selectedCell = null))
            }) { Text("Clear") }

            if (cell.isFilled) {
                TextButton(onClick = {
                    val flipped = if (cell.source == CellSource.GIVEN) CellSource.GUESS else CellSource.GIVEN
                    onChange(state.withCell(index, cell.digit, flipped).copy(selectedCell = null))
                }) {
                    Text(if (cell.source == CellSource.GIVEN) "It is handwritten" else "It is printed")
                }
            }
            TextButton(onClick = { onChange(state.copy(selectedCell = null)) }) { Text("Close") }
        }
    }
}

/** Draws the overlay in cell coordinates: the rectified photo is a square, so this is just ninths. */
private fun DrawScope.drawOverlay(
    state: PuzzleState,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val cell = size.width / 9f

    fun cellTopLeft(index: Int) =
        androidx.compose.ui.geometry.Offset((index % 9) * cell, (index / 9) * cell)

    for (index in state.uncertainCells) {
        drawRect(
            color = Color(0x55FFD54F),
            topLeft = cellTopLeft(index),
            size = androidx.compose.ui.geometry.Size(cell, cell),
        )
    }

    for (index in state.highlightedCells()) {
        drawRect(
            color = Color(0x3364B5F6),
            topLeft = cellTopLeft(index),
            size = androidx.compose.ui.geometry.Size(cell, cell),
        )
    }

    state.selectedCell?.let { index ->
        drawRect(
            color = Color(0xFFFFFFFF),
            topLeft = cellTopLeft(index),
            size = androidx.compose.ui.geometry.Size(cell, cell),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
        )
    }

    for ((index, digit) in state.overlayDigits()) {
        val colour = when (digit.role) {
            OverlayRole.FILLED -> Color(0xFF4FC3F7)
            OverlayRole.CORRECT -> Color(0xFF81C784)
            OverlayRole.INCORRECT -> Color(0xFFE57373)
            OverlayRole.HINT -> Color(0xFFFFD54F)
        }
        if (digit.role == OverlayRole.CORRECT) {
            // The paper already shows the right digit, so a tint is enough.
            drawRect(
                color = colour.copy(alpha = 0.30f),
                topLeft = cellTopLeft(index),
                size = androidx.compose.ui.geometry.Size(cell, cell),
            )
        } else if (digit.role == OverlayRole.INCORRECT) {
            // Tint, and then draw what the app read on top. Without the digit, a
            // misreading is indistinguishable from the app marking a correct answer
            // wrong, which is exactly how it was first reported.
            drawRect(
                color = colour.copy(alpha = 0.45f),
                topLeft = cellTopLeft(index),
                size = androidx.compose.ui.geometry.Size(cell, cell),
            )
            val layout = measurer.measure(
                digit.digit.toString(),
                style = TextStyle(
                    color = Color(0xFF8E0000),
                    fontSize = (cell * 0.44f).toSp(),
                    fontWeight = FontWeight.Bold,
                ),
            )
            val origin = cellTopLeft(index)
            drawText(
                layout,
                topLeft = androidx.compose.ui.geometry.Offset(
                    origin.x + cell - layout.size.width - cell * 0.04f,
                    origin.y + cell * 0.02f,
                ),
            )
        } else {
            val layout = measurer.measure(
                digit.digit.toString(),
                style = TextStyle(color = colour, fontSize = (cell * 0.62f).toSp(), fontWeight = FontWeight.Bold),
            )
            val origin = cellTopLeft(index)
            drawText(
                layout,
                topLeft = androidx.compose.ui.geometry.Offset(
                    origin.x + (cell - layout.size.width) / 2f,
                    origin.y + (cell - layout.size.height) / 2f,
                ),
            )
        }
    }
}

private fun Float.toSp() = (this / 2.2f).sp

package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.tonyxmelon.aisudoku.solver.AnswerCheck
import io.github.tonyxmelon.aisudoku.solver.AnswerChecker

/**
 * Every puzzle read so far, newest first, under day headings.
 *
 * Lives in the navigation drawer rather than on a screen of its own: it is a list you
 * reach for while looking at a puzzle, and sending the user somewhere else to pick one
 * only to send them straight back is a longer way round to the same place.
 *
 * Grouped by day because the list only grows, and a date is how anyone actually
 * remembers which puzzle they want.
 */
@Composable
fun HistoryList(
    history: History,
    entries: List<HistoryEntry>,
    currentId: Long?,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onCamera: () -> Unit,
    onClose: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }

    LazyColumn(
        // No inset padding here: ModalDrawerSheet already applies the system bars, and
        // adding them again pushes the first entry a status bar's height down the sheet.
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A way out that does not depend on finding the scrim. The sheet is as wide as
        // a small phone's whole screen, so there may be no scrim to tap at all.
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Your puzzles",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        }

        // The way to a new puzzle, above the old ones. The camera also has its own icon
        // in the bar, but this is the menu you open when the question is "which puzzle" -
        // and "a new one" is one of the answers to that question.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCamera)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CameraIcon, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("New sudoku", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Photograph another puzzle.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    "Puzzles you photograph are kept here, so you can pick one up again later.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        for ((day, group) in History.byDay(entries)) {
            item {
                Text(
                    day,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            for (entry in group) {
                item(key = entry.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(52.dp)) {
                            history.loadPhoto(entry)?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                History.timeOf(entry),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (entry.id == currentId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(summarise(entry), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { pendingDelete = entry }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete this puzzle")
                        }
                    }
                }
            }
        }

        item { Box(Modifier.size(24.dp)) }
    }

    // Deleting throws away the photograph as well as the grid, and there is no undo, so
    // it is worth one tap to be sure.
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete this puzzle?") },
            text = {
                Text(
                    "The photo from ${History.timeOf(entry)} and everything read from it " +
                        "will be removed from this phone. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(entry)
                    pendingDelete = null
                }) {
                    Text("Delete", color = Overlays.incorrect)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep") }
            },
        )
    }
}

/** A line telling you which puzzle this is without opening it. */
private fun summarise(entry: HistoryEntry): String {
    val given = entry.grid.givenCount
    val written = entry.grid.filledCount - given
    val progress = when {
        entry.grid.isComplete -> "finished"
        written == 0 -> "not started"
        else -> "$written written in"
    }
    val wrong = (AnswerChecker.check(entry.grid) as? AnswerCheck.Checked)?.incorrect?.size ?: 0
    return if (wrong > 0) "$given printed, $progress, $wrong wrong" else "$given printed, $progress"
}

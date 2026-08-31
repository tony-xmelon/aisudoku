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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * Grouped by day rather than shown as one long list, because the list only grows and a
 * date is how anyone actually remembers which puzzle they want.
 */
@Composable
fun HistoryScreen(
    history: History,
    entries: List<HistoryEntry>,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = "History", onBack = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        "Puzzles you photograph are kept here, so you can pick one up again later.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            for ((day, group) in History.byDay(entries)) {
                item {
                    Text(
                        day,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items@ for (entry in group) {
                    item(key = entry.id) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(entry) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(64.dp)) {
                                history.loadPhoto(entry)?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            Column(modifier = Modifier.fillMaxWidth(0.6f)) {
                                Text(History.timeOf(entry), style = MaterialTheme.typography.titleMedium)
                                Text(summarise(entry), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onDelete(entry) }) { Text("Delete") }
                        }
                    }
                }
            }

        }
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

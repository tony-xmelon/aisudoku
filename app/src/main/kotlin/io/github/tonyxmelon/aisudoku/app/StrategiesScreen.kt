package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tonyxmelon.aisudoku.solver.Difficulty
import io.github.tonyxmelon.aisudoku.solver.Technique
import io.github.tonyxmelon.aisudoku.solver.Techniques

/**
 * The four ways of reasoning this app knows, and how to spot each one.
 *
 * The same text the hints and the walkthrough quote, gathered in one place so it can be
 * read on purpose rather than only met in passing. Written to be read while holding a
 * pencil: the app can already finish any puzzle it can read, so the only reason to name a
 * technique is so the user can find the next one without it.
 */
@Composable
fun StrategiesScreen(onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = "Strategies", onBack = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Text(
                    "Every sudoku that has one answer can be reached by reasoning; guessing " +
                        "is never required and rarely helps. These are the four kinds of " +
                        "reasoning this app can recognise and explain, easiest first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            items@ for (technique in Techniques.all) {
                item(key = technique.name) { Strategy(technique) }
            }

            item {
                Text(
                    "Harder puzzles need more than these. When the app says it has run out " +
                        "of techniques, that is what has happened - not that the puzzle is " +
                        "unsolvable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Strategy(technique: Technique) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                technique.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                label(technique.difficulty),
                style = MaterialTheme.typography.labelSmall,
                color = colour(technique.difficulty),
                modifier = Modifier
                    .background(
                        colour(technique.difficulty).copy(alpha = 0.16f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(technique.rule, style = MaterialTheme.typography.bodyMedium)
        Text(
            technique.howTo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun label(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> "Easiest"
    Difficulty.MEDIUM -> "Middling"
    Difficulty.HARD -> "Hardest"
    Difficulty.VERY_HARD -> "Beyond this app"
}

private fun colour(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Overlays.correct
    Difficulty.MEDIUM -> Overlays.uncertain
    Difficulty.HARD, Difficulty.VERY_HARD -> Overlays.incorrect
}

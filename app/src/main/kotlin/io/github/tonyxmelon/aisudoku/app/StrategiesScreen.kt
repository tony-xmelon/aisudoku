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
import androidx.compose.material3.FilledTonalButton
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
 * Every way of reasoning this app knows, and how to spot each one.
 *
 * The same text the hints and the tutor quote, gathered in one place so it can be read on
 * purpose rather than only met in passing. Written to be read while holding a pencil: the
 * app can already finish any puzzle it can read, so the only reason to name a technique is
 * so the user can find the next one without it.
 *
 * Each one also offers to show every place it applies in the puzzle currently open, which
 * teaches far more than the paragraph above it does.
 */
@Composable
fun StrategiesScreen(
    /** How many places each technique applies in the puzzle on screen, if there is one. */
    findings: Map<String, Int>,
    onExplore: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = "Strategies", onBack = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Text(
                    "Every sudoku with one answer can be reached by reasoning; guessing is " +
                        "never required and rarely helps. These are the ways of reasoning " +
                        "this app can recognise and explain, easiest first. Where one of " +
                        "them applies to the puzzle you have open, it will show you every " +
                        "place it does.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            items@ for (technique in Techniques.all) {
                item(key = technique.name) {
                    Strategy(
                        technique = technique,
                        available = findings[technique.name],
                        onExplore = { onExplore(technique.name) },
                    )
                }
            }

            item {
                Text(
                    "A technique list is never complete, and the hardest puzzles are built " +
                        "to defeat whatever is on it. So when every one of these comes up " +
                        "empty, the tutor settles a single square by trying its candidates " +
                        "out, says plainly that is what it has done, and carries on " +
                        "reasoning from there. It always reaches the end of a puzzle, and it " +
                        "never pretends a lookup was a deduction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Strategy(technique: Technique, available: Int?, onExplore: () -> Unit) {
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

        // Reading about a technique teaches far less than being shown four of them on the
        // grid in front of you, so every one that applies right now offers to do that.
        when {
            available == null -> Unit

            available == 0 -> Text(
                "None on your puzzle at the moment.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> FilledTonalButton(onClick = onExplore) {
                Text(
                    if (available == 1) "Show me the one on your puzzle"
                    else "Show me all $available on your puzzle"
                )
            }
        }
    }
}

private fun label(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> "Gentle"
    Difficulty.MEDIUM -> "Moderate"
    Difficulty.HARD -> "Hard"
    Difficulty.VERY_HARD -> "Very hard"
}

private fun colour(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Overlays.correct
    Difficulty.MEDIUM -> Overlays.uncertain
    Difficulty.HARD, Difficulty.VERY_HARD -> Overlays.incorrect
}

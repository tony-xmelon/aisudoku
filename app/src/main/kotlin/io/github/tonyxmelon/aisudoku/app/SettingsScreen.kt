package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tonyxmelon.aisudoku.BuildConfig

/**
 * Settings, about and the legal notices, on one scrolling page.
 *
 * One page rather than three, because there is not enough in any of them to justify
 * navigation the user then has to come back out of.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = "Settings", onBack = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.fillMaxWidth(0.75f)) {
                        Text("Explain hints", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Name the technique and show the cells that prove it, instead of " +
                                "just giving the digit.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = settings.hintStyle == HintStyle.EXPLAIN,
                        onCheckedChange = {
                            onChange(settings.copy(hintStyle = if (it) HintStyle.EXPLAIN else HintStyle.REVEAL))
                        },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.fillMaxWidth(0.75f)) {
                        Text("Take the photo automatically", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Fire the shutter once the grid is square in frame and steady. " +
                                "The button always works either way.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = settings.autoCapture,
                        onCheckedChange = { onChange(settings.copy(autoCapture = it)) },
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About", style = MaterialTheme.typography.headlineSmall)
                    Text("AI Sudoku ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Point the camera at a sudoku puzzle on paper. The app reads the printed " +
                            "digits and anything you have written in, solves it, and can give you a " +
                            "hint, check your answers, or show the whole solution.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "It reads printed digits reliably. Handwriting is harder - expect the odd " +
                            "misread, and tap any cell to correct it. When the app is unsure it says " +
                            "so and asks, rather than guessing: a confidently wrong grid is the worst " +
                            "thing it could hand you.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your photos and your privacy", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Everything happens on this phone. The app has no internet permission at " +
                            "all, so your photographs cannot leave the device even by accident, and " +
                            "nothing is collected, tracked or uploaded. There are no accounts and no " +
                            "analytics.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Photographs you keep in history are stored in this app's private storage " +
                            "and are removed when you delete them or uninstall the app.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Open source notices", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "OpenCV - Apache License 2.0. Used for finding and straightening the grid.\n\n" +
                            "AndroidX, Jetpack Compose and CameraX - Apache License 2.0.\n\n" +
                            "Kotlin - Apache License 2.0.\n\n" +
                            "The digit recogniser was trained on the MNIST database of handwritten " +
                            "digits by LeCun, Cortes and Burges, together with digits rendered from " +
                            "open system fonts.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Puzzles photographed for testing came from sudoku.cba.si. This app is not " +
                            "affiliated with them.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Text(
                    "Sudoku is a public-domain puzzle form. This app solves puzzles you already " +
                        "have; it does not reproduce or distribute anyone's puzzles.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                )
            }

        }
    }
}

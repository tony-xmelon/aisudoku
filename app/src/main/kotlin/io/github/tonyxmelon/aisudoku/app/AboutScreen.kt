package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tonyxmelon.aisudoku.BuildConfig

/**
 * What the app is, what it does with your photographs, and what it is built on.
 *
 * Reached from its own button in the bar rather than through settings: none of it is a
 * setting, and a page of prose behind a row of switches is a page nobody reads twice.
 */
@Composable
fun AboutScreen(onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = "About", subtitle = "AI Sudoku ${BuildConfig.VERSION_NAME}", onBack = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Section("What it does") {
                    Text(
                        "Point the camera at a sudoku puzzle on paper. The app reads the " +
                            "printed digits and anything you have written in, solves it, and " +
                            "can give you a hint, check your answers, or show the whole " +
                            "solution.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "It reads printed digits reliably. Handwriting is harder - expect the " +
                            "odd misread, and tap any square to correct it. When the app is " +
                            "unsure it says so and asks, rather than guessing: a confidently " +
                            "wrong grid is the worst thing it could hand you. \"What was read\" " +
                            "shows exactly what it made of every square, and how sure it was.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Section("Your photos and your privacy") {
                    Text(
                        "Everything happens on this phone. The app has no internet permission " +
                            "at all, so your photographs cannot leave the device even by " +
                            "accident, and nothing is collected, tracked or uploaded. There " +
                            "are no accounts and no analytics.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Photographs you keep are stored in this app's private storage and are " +
                            "removed when you delete them or uninstall the app.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Section("Open source notices") {
                    Text(
                        "OpenCV - Apache License 2.0. Used for finding and straightening the " +
                            "grid.\n\n" +
                            "AndroidX, Jetpack Compose and CameraX - Apache License 2.0.\n\n" +
                            "Kotlin - Apache License 2.0.\n\n" +
                            "The digit recogniser was trained on the MNIST database of " +
                            "handwritten digits by LeCun, Cortes and Burges, together with " +
                            "digits rendered from open system fonts and digits drawn " +
                            "programmatically.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Section("Puzzles") {
                    Text(
                        "Sudoku is a public-domain puzzle form. This app solves puzzles you " +
                            "already have; it does not reproduce or distribute anyone's " +
                            "puzzles.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Puzzles photographed for testing came from sudoku.cba.si. This app is " +
                            "not affiliated with them.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

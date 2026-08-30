package io.github.tonyxmelon.aisudoku.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import org.opencv.android.OpenCVLoader

/** Which screen is showing. */
private enum class Screen { CAMERA, PUZZLE, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The vision module never links against a platform, so the loader is injected.
        OpenCvNatives.ensureLoaded {
            check(OpenCVLoader.initLocal()) { "OpenCV native libraries failed to load" }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val history = remember { History(context) }

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    var settings by remember { mutableStateOf(Settings.load(context)) }
    var puzzle by remember { mutableStateOf<PuzzleState?>(null) }
    var entries by remember { mutableStateOf(history.list()) }
    var screen by remember { mutableStateOf(Screen.CAMERA) }

    fun applySettings(updated: Settings) {
        settings = updated
        Settings.save(context, updated)
        // A puzzle already on screen should follow the setting rather than keep the old one.
        puzzle = puzzle?.copy(hintStyle = updated.hintStyle, revealedHintDigit = false)
    }

    when {
        !hasCamera -> PermissionScreen { request.launch(Manifest.permission.CAMERA) }

        screen == Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            onChange = ::applySettings,
            onClose = { screen = if (puzzle != null) Screen.PUZZLE else Screen.CAMERA },
        )

        screen == Screen.HISTORY -> HistoryScreen(
            history = history,
            entries = entries,
            onOpen = { entry ->
                history.loadPhoto(entry)?.let { photo ->
                    puzzle = PuzzleState(
                        photo = photo,
                        grid = entry.grid,
                        uncertainCells = emptySet(),
                        message = null,
                        hintStyle = settings.hintStyle,
                    )
                    screen = Screen.PUZZLE
                }
            },
            onDelete = { entry ->
                history.delete(entry)
                entries = history.list()
            },
            onClose = { screen = Screen.CAMERA },
        )

        screen == Screen.PUZZLE && puzzle != null -> PuzzleScreen(
            state = puzzle!!,
            onChange = { puzzle = it },
            onRetake = { puzzle = null; screen = Screen.CAMERA },
            onSettings = { screen = Screen.SETTINGS },
            onHistory = { entries = history.list(); screen = Screen.HISTORY },
        )

        else -> CameraScreen(
            autoCapture = settings.autoCapture,
            onRead = { state ->
                // Saved as soon as it is read, so a puzzle is never lost by backing out.
                history.save(state.photo, state.grid)
                entries = history.list()
                puzzle = state.copy(hintStyle = settings.hintStyle)
                screen = Screen.PUZZLE
            },
            onSettings = { screen = Screen.SETTINGS },
            onHistory = { entries = history.list(); screen = Screen.HISTORY },
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                "AI Sudoku reads puzzles through the camera, so it needs camera access. " +
                    "Nothing leaves your phone - the app has no internet permission at all.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onRequest) { Text("Allow camera") }
        }
    }
}

/** The bar of secondary destinations, shared by the camera and puzzle screens. */
@Composable
fun NavigationRow(onHistory: () -> Unit, onSettings: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onHistory) { Text("History") }
        TextButton(onClick = onSettings) { Text("Settings") }
    }
}

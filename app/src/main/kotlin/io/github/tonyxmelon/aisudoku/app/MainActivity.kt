package io.github.tonyxmelon.aisudoku.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.tonyxmelon.aisudoku.solver.TechniqueSolver
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

/** Which screen is showing. History is a drawer over whichever of these is up. */
private enum class Screen { CAMERA, PUZZLE, SETTINGS, ABOUT, STRATEGIES }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the status and navigation bars, and let each screen inset its own
        // content. The camera wants the preview under them; nothing else does.
        //
        // The bars are pinned dark rather than left on auto: this app is dark whatever
        // the phone is set to, so on a phone in light mode `auto` would put dark system
        // icons on a dark background and they would vanish.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

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
    val scope = rememberCoroutineScope()

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

    // Which history entry the puzzle on screen belongs to, so corrections are written
    // back to it. Without this, reopening a puzzle undoes every fix the user made.
    var entryId by remember { mutableStateOf<Long?>(null) }

    val drawer = rememberDrawerState(DrawerValue.Closed)

    fun closeDrawer() {
        scope.launch { drawer.close() }
    }

    fun openDrawer() {
        entries = history.list()
        scope.launch { drawer.open() }
    }

    fun editPuzzle(updated: PuzzleState) {
        puzzle = updated
        entryId?.let { history.update(it, updated.grid) }
    }

    fun applySettings(updated: Settings) {
        settings = updated
        Settings.save(context, updated)
        // A puzzle already on screen should follow the setting rather than keep the old one.
        puzzle = puzzle?.copy(hintStyle = updated.hintStyle)
    }

    fun leaveOverlay() {
        screen = if (puzzle != null) Screen.PUZZLE else Screen.CAMERA
    }

    fun newPhoto() {
        puzzle = null
        entryId = null
        screen = Screen.CAMERA
    }

    if (!hasCamera) {
        PermissionScreen { request.launch(Manifest.permission.CAMERA) }
        return
    }

    // Back has to mean something everywhere it can. Without this the drawer had no way
    // out at all on a narrow phone, where the sheet is as wide as the screen and leaves
    // no scrim to tap, and back from settings or about left the app entirely.
    BackHandler(enabled = drawer.isOpen) { closeDrawer() }
    BackHandler(
        enabled = !drawer.isOpen &&
            screen in setOf(Screen.ABOUT, Screen.SETTINGS, Screen.STRATEGIES),
    ) {
        screen = if (puzzle != null) Screen.PUZZLE else Screen.CAMERA
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        // A settings or about screen is somewhere you went on purpose; sliding history in
        // over it would be answering a question nobody asked.
        gesturesEnabled = drawer.isOpen || screen == Screen.CAMERA || screen == Screen.PUZZLE,
        // A reading page is somewhere you went on purpose; sliding history in over it
        // would be answering a question nobody asked.
        drawerContent = {
            // Narrower than the screen on purpose, so there is always a strip of scrim to
            // tap. The default is 360dp, which is wider than a small phone.
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.84f)) {
                HistoryList(
                    history = history,
                    entries = entries,
                    currentId = entryId,
                    onOpen = { entry ->
                        history.loadPhoto(entry)?.let { photo ->
                            entryId = entry.id
                            puzzle = PuzzleState(
                                photo = photo,
                                grid = entry.grid,
                                uncertainCells = emptySet(),
                                readingNote = null,
                                hintStyle = settings.hintStyle,
                            )
                            screen = Screen.PUZZLE
                        }
                        scope.launch { drawer.close() }
                    },
                    onDelete = { entry ->
                        history.delete(entry)
                        entries = history.list()
                        // The puzzle on screen has just been thrown away, so leave it.
                        if (entryId == entry.id) newPhoto()
                    },
                    onStrategies = {
                        screen = Screen.STRATEGIES
                        closeDrawer()
                    },
                    onClose = ::closeDrawer,
                )
            }
        },
    ) {
        when {
            screen == Screen.STRATEGIES -> StrategiesScreen(
                findings = puzzle?.let { TechniqueSolver.findingCounts(it.grid) }.orEmpty(),
                onExplore = { technique ->
                    puzzle = puzzle?.tutor(technique)
                    screen = Screen.PUZZLE
                },
                onClose = ::leaveOverlay,
            )

            screen == Screen.ABOUT -> AboutScreen(onClose = ::leaveOverlay)

            screen == Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                onChange = ::applySettings,
                onClose = ::leaveOverlay,
            )

            screen == Screen.PUZZLE && puzzle != null -> PuzzleScreen(
                state = puzzle!!,
                onChange = ::editPuzzle,
                onMenu = ::openDrawer,
                onRetake = ::newPhoto,
                onSettings = { screen = Screen.SETTINGS },
                onAbout = { screen = Screen.ABOUT },
            )

            else -> CameraScreen(
                autoCapture = settings.autoCapture,
                onRead = { state ->
                    // Saved as soon as it is read, so a puzzle is never lost by backing out.
                    entryId = history.save(state.photo, state.grid).id
                    entries = history.list()
                    puzzle = state.copy(hintStyle = settings.hintStyle)
                    screen = Screen.PUZZLE
                },
                onMenu = ::openDrawer,
                onSettings = { screen = Screen.SETTINGS },
                onAbout = { screen = Screen.ABOUT },
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
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

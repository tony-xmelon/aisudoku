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
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

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
    // Photographs the app refused. Looked up with the puzzles, since the drawer shows both.
    var refused by remember { mutableStateOf(Diagnostics.refused(context)) }
    var nav by remember { mutableStateOf(Navigation(Screen.CAMERA)) }
    val screen = nav.screen

    // Which history entry the puzzle on screen belongs to, so corrections are written
    // back to it. Without this, reopening a puzzle undoes every fix the user made.
    var entryId by remember { mutableStateOf<Long?>(null) }

    val drawer = rememberDrawerState(DrawerValue.Closed)

    fun closeDrawer() {
        scope.launch { drawer.close() }
    }

    fun openDrawer() {
        entries = history.list()
        refused = Diagnostics.refused(context)
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
        puzzle = puzzle?.copy(
            hintStyle = updated.hintStyle,
            routeStyle = updated.routeStyle,
        )
    }

    fun go(target: Screen) {
        nav = nav.go(target)
    }

    /** The close button on a screen you opened. The same thing Back does there. */
    fun leaveOverlay() {
        nav = nav.back() ?: nav.reset(if (puzzle != null) Screen.PUZZLE else Screen.CAMERA)
    }

    /**
     * Going to the camera without throwing the puzzle away.
     *
     * Keeping it is what makes Back from the camera mean "never mind" rather than "leave
     * the app". A photograph that is actually taken replaces it.
     */
    fun takePhoto() {
        go(Screen.CAMERA)
    }

    /** The puzzle on screen is gone - deleted - so there is nothing to go back to. */
    fun discardPuzzle() {
        puzzle = null
        entryId = null
        nav = nav.reset(Screen.CAMERA)
    }

    if (!hasCamera) {
        PermissionScreen { request.launch(Manifest.permission.CAMERA) }
        return
    }

    // Back has to mean something everywhere it can. Every one of these was, at some
    // point, a press that closed the app instead: the drawer has no scrim to tap on a
    // narrow phone, and settings, about, the tutor and the puzzle itself all sat one
    // press from the door.
    //
    // What Back undoes is the last thing that appeared: the drawer, then a screen you
    // opened, then a layer over the puzzle, then the puzzle itself. Only on the camera
    // with nothing behind it does Back leave, which is the one place Android expects to
    // be let out of. The conditions are exclusive, but they are still written
    // outermost-first: the dispatcher runs the last enabled handler declared, so the
    // drawer - the topmost thing on screen whenever it is open - goes at the bottom.
    BackHandler(enabled = !drawer.isOpen && nav.canGoBack) {
        nav.back()?.let { nav = it }
    }
    BackHandler(
        enabled = !drawer.isOpen &&
            screen == Screen.PUZZLE &&
            puzzle?.overlay?.let { it != OverlayMode.NONE } == true,
    ) {
        puzzle = puzzle?.close()
    }
    BackHandler(enabled = drawer.isOpen) { closeDrawer() }

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
                                routeStyle = settings.routeStyle,
                            )
                            go(Screen.PUZZLE)
                        }
                        scope.launch { drawer.close() }
                    },
                    onDelete = { entry ->
                        history.delete(entry)
                        entries = history.list()
                        // The puzzle on screen has just been thrown away, so leave it.
                        if (entryId == entry.id) discardPuzzle()
                    },
                    onCamera = {
                        takePhoto()
                        closeDrawer()
                    },
                    refused = refused,
                    onDiscard = { scan ->
                        Diagnostics.discard(scan)
                        refused = Diagnostics.refused(context)
                    },
                    onClose = ::closeDrawer,
                )
            }
        },
    ) {
        when {
            screen == Screen.STRATEGIES -> StrategiesScreen(
                // PuzzleState already caches this; calling the solver here instead ran
                // all twenty-three techniques again on every recomposition, on the main
                // thread, for a number it was holding the whole time.
                findings = puzzle?.findingCounts.orEmpty(),
                onExplore = { technique ->
                    puzzle = puzzle?.tutor(technique)
                    leaveOverlay()
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
                onRetake = ::takePhoto,
                onStrategies = { go(Screen.STRATEGIES) },
                onSettings = { go(Screen.SETTINGS) },
                onAbout = { go(Screen.ABOUT) },
            )

            else -> CameraScreen(
                autoCapture = settings.autoCapture,
                onRead = { state ->
                    // Saved as soon as it is read, so a puzzle is never lost by backing out.
                    entryId = history.save(state.photo, state.grid).id
                    entries = history.list()
                    puzzle = state.copy(
                        hintStyle = settings.hintStyle,
                        routeStyle = settings.routeStyle,
                    )
                    go(Screen.PUZZLE)
                },
                onMenu = ::openDrawer,
                onStrategies = { go(Screen.STRATEGIES) },
                onSettings = { go(Screen.SETTINGS) },
                onAbout = { go(Screen.ABOUT) },
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

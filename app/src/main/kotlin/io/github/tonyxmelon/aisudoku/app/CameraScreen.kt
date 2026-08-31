package io.github.tonyxmelon.aisudoku.app

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.tonyxmelon.aisudoku.recognize.GridReader
import io.github.tonyxmelon.aisudoku.recognize.ReadResult
import io.github.tonyxmelon.aisudoku.vision.FramingAdvisor
import io.github.tonyxmelon.aisudoku.vision.GateVerdict
import io.github.tonyxmelon.aisudoku.vision.StructuralGate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live camera with framing guidance and automatic capture.
 *
 * The preview fills the screen, the way a camera does, and the square is drawn on top of
 * it as a guide. Making the preview itself square put it under the notch and left half
 * the screen black.
 *
 * Analysis runs on a single background thread and drops frames it cannot keep up with,
 * so guidance stays responsive rather than accurate to the last frame.
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScreen(
    autoCapture: Boolean,
    onRead: (PuzzleState) -> Unit,
    onMenu: () -> Unit,
    onStrategies: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var guidance by remember { mutableStateOf("Point the camera at a sudoku puzzle") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val advisor = remember { FramingAdvisor() }
    val capturing = remember { AtomicBoolean(false) }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val previewView = remember { PreviewView(context) }

    fun handleCaptured(proxy: ImageProxy) {
        try {
            val buffer = proxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val image = Images.fromJpeg(bytes, proxy.imageInfo.rotationDegrees)

            when (val verdict = StructuralGate.assess(image)) {
                is GateVerdict.Rejected -> failure = verdict.reason.message

                is GateVerdict.Usable -> {
                    val lines = GridLines(
                        vertical = verdict.geometry.verticalLines
                            .map { (it / verdict.rectified.width).toFloat() },
                        horizontal = verdict.geometry.horizontalLines
                            .map { (it / verdict.rectified.height).toFloat() },
                    )
                    when (val read = GridReader().read(verdict.cells)) {
                        is ReadResult.Unreadable -> failure = read.reason
                        is ReadResult.Accepted -> onRead(
                            PuzzleState(
                                photo = Images.toBitmap(verdict.rectified),
                                grid = read.grid,
                                uncertainCells = emptySet(),
                                readingNote = null,
                                lines = lines,
                                reports = read.readings.map(CellReport::of),
                            )
                        )

                        is ReadResult.NeedsConfirmation -> onRead(
                            PuzzleState(
                                photo = Images.toBitmap(verdict.rectified),
                                grid = read.grid,
                                uncertainCells = read.uncertainCells,
                                readingNote = read.reason,
                                lines = lines,
                                reports = read.readings.map(CellReport::of),
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            failure = e.message ?: "Something went wrong reading that photo."
        } finally {
            proxy.close()
            busy = false
            capturing.set(false)
            advisor.reset()
        }
    }

    fun takePicture() {
        if (!capturing.compareAndSet(false, true)) return
        busy = true
        failure = null
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = handleCaptured(image)

                override fun onError(exception: ImageCaptureException) {
                    failure = "The camera could not take that photo."
                    busy = false
                    capturing.set(false)
                }
            },
        )
    }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(960, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(analysisExecutor) { proxy ->
                try {
                    if (!capturing.get()) {
                        val advice = advisor.advise(Images.fromPreview(proxy))
                        guidance = advice.message
                        if (autoCapture && advice.readyToCapture) takePicture()
                    }
                } catch (_: Exception) {
                    // A frame we cannot make sense of is not worth reporting; the next
                    // one is milliseconds away.
                } finally {
                    proxy.close()
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, capture,
            )
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(onMenu, Icons.Filled.Menu, "Your puzzles")
                Box(Modifier.weight(1f))
                OverflowMenu(onStrategies, onSettings, onAbout, glass = true)
            }

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                Reticle(modifier = Modifier.size(side))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    color = Color(0x99000000),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = failure ?: guidance,
                        color = if (failure != null) Overlays.incorrect else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (busy) {
                    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    Shutter(onClick = { takePicture() })
                }
            }
        }
    }
}

/** The square the puzzle should sit inside: four corner brackets, drawn over the preview. */
@Composable
private fun Reticle(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val arm = size.minDimension * 0.14f
        val width = 4.dp.toPx()
        val colour = Color.White.copy(alpha = 0.85f)
        val corners = listOf(
            Offset(0f, 0f) to listOf(Offset(arm, 0f), Offset(0f, arm)),
            Offset(size.width, 0f) to listOf(Offset(size.width - arm, 0f), Offset(size.width, arm)),
            Offset(0f, size.height) to
                listOf(Offset(arm, size.height), Offset(0f, size.height - arm)),
            Offset(size.width, size.height) to listOf(
                Offset(size.width - arm, size.height),
                Offset(size.width, size.height - arm),
            ),
        )
        for ((corner, arms) in corners) {
            for (end in arms) {
                drawLine(colour, corner, end, strokeWidth = width, cap = StrokeCap.Round)
            }
        }
    }
}

/** The shutter: a ring with a disc inside it, the shape everyone already knows. */
@Composable
private fun Shutter(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clickable(onClick = onClick)
            .background(Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(4.dp, Color.White),
        ) {}
        Box(Modifier.size(58.dp).background(Color.White, CircleShape))
    }
}

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
 * Analysis runs on a single background thread and drops frames it cannot keep up with,
 * so guidance stays responsive rather than accurate to the last frame.
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScreen(
    autoCapture: Boolean,
    onRead: (PuzzleState) -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
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
                is GateVerdict.Rejected -> {
                    failure = verdict.reason.message
                }

                is GateVerdict.Usable -> {
                    when (val read = GridReader().read(verdict.cells)) {
                        is ReadResult.Unreadable -> failure = read.reason
                        is ReadResult.Accepted -> onRead(
                            PuzzleState(
                                photo = Images.toBitmap(verdict.rectified),
                                grid = read.grid,
                                uncertainCells = emptySet(),
                                message = null,
                            )
                        )
                        is ReadResult.NeedsConfirmation -> onRead(
                            PuzzleState(
                                photo = Images.toBitmap(verdict.rectified),
                                grid = read.grid,
                                uncertainCells = read.uncertainCells,
                                message = read.reason,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A square viewfinder, because the thing being photographed is square. It also
        // stops the guidance and the shutter competing with the preview for space.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .weight(1f, fill = false),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = failure ?: guidance,
                color = if (failure != null) Color(0xFFFF8A80) else Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            if (busy) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Button(onClick = { takePicture() }) { Text("Capture") }
            }
            NavigationRow(onHistory = onHistory, onSettings = onSettings)
        }
    }
}

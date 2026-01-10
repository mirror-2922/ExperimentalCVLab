package com.mirror2922.ecvl.ui.camera

import android.graphics.ImageFormat
import android.graphics.RectF
import android.media.ImageReader
import android.os.Handler
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mirror2922.ecvl.NativeLib
import com.mirror2922.ecvl.util.HardwareMonitor
import com.mirror2922.ecvl.viewmodel.AppMode
import com.mirror2922.ecvl.viewmodel.BeautyViewModel
import com.mirror2922.ecvl.viewmodel.Detection
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

@Composable
fun CameraView(viewModel: BeautyViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nativeLib = remember { NativeLib() }
    var viewfinderSurface by remember { mutableStateOf<Surface?>(null) }
    
    // Face Detection Setup
    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        FaceDetection.getClient(options)
    }
    
    val mlKitReader = remember {
        ImageReader.newInstance(640, 640, ImageFormat.YUV_420_888, 2)
    }

    // 1. Model Auto-loading for AI Mode
    LaunchedEffect(viewModel.currentMode, viewModel.currentModelId) {
        if (viewModel.currentMode == AppMode.AI && viewModel.currentModelId.isNotEmpty()) {
            val modelFile = File(context.filesDir, "${viewModel.currentModelId}.onnx")
            if (modelFile.exists()) {
                viewModel.isLoading = true
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    nativeLib.initYolo(modelFile.absolutePath)
                }
                viewModel.isLoading = false
            }
        }
    }

    // 2. Pure Kotlin Performance Polling
    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                val perf = nativeLib.getPerfMetricsBinary()
                if (perf.size >= 4) {
                    viewModel.currentFps = perf[0]
                    viewModel.inferenceTime = perf[1].toLong()
                    viewModel.actualCameraSize = "${perf[2].toInt()}x${perf[3].toInt()}"
                    viewModel.actualBackendSize = if (viewModel.currentMode == AppMode.AI) "640x640" else viewModel.actualCameraSize
                }
                viewModel.cpuUsage = HardwareMonitor.getCpuUsage()
            } catch (e: Exception) {
                Log.e("CameraView", "HUD Polling error", e)
            }
            delay(33) // ~30Hz is enough for HUD
        }
    }

    // 3. ML Kit Face Detection (Kotlin Composite)
    DisposableEffect(mlKitReader) {
        val listener = ImageReader.OnImageAvailableListener { reader ->
            val image = try { reader.acquireLatestImage() } catch (e: Exception) { null } ?: return@OnImageAvailableListener
            if (viewModel.currentMode == AppMode.FACE) {
                val inputImage = InputImage.fromMediaImage(image, 0)
                faceDetector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        val detections = faces.map { face ->
                            Detection(
                                label = "Face",
                                confidence = 1.0f,
                                boundingBox = RectF(
                                    face.boundingBox.left.toFloat() / image.width,
                                    face.boundingBox.top.toFloat() / image.height,
                                    face.boundingBox.right.toFloat() / image.width,
                                    face.boundingBox.bottom.toFloat() / image.height
                                ),
                                id = face.trackingId
                            )
                        }
                        viewModel.detections.clear()
                        viewModel.detections.addAll(detections)
                    }
            }
            image.close()
        }
        mlKitReader.setOnImageAvailableListener(listener, Handler(android.os.Looper.getMainLooper()))
        onDispose { mlKitReader.setOnImageAvailableListener(null, null) }
    }

    // 4. NDK Camera Lifecycle
    DisposableEffect(viewModel.lensFacing, viewfinderSurface) {
        val vs = viewfinderSurface
        if (vs != null) {
            // Constant 1280x720 internal processing for stability
            nativeLib.startNativeCamera(if (viewModel.lensFacing == 1) 1 else 0, 1280, 720, vs, mlKitReader.surface)
            nativeLib.updateNativeConfig(
                when(viewModel.currentMode) {
                    AppMode.AI -> 1
                    AppMode.FACE -> 2
                    else -> 0
                },
                viewModel.selectedFilter
            )
        }
        onDispose { nativeLib.stopNativeCamera() }
    }

    // 5. Dynamic State Sync
    LaunchedEffect(viewModel.currentMode, viewModel.selectedFilter) {
        nativeLib.updateNativeConfig(
            when(viewModel.currentMode) {
                AppMode.AI -> 1
                AppMode.FACE -> 2
                else -> 0
            },
            viewModel.selectedFilter
        )
        if (viewModel.currentMode != AppMode.FACE) {
            viewModel.detections.clear()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Native Rendering (Viewfinder + NDK Composited AI)
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) { viewfinderSurface = h.surface }
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h1: Int) { viewfinderSurface = h.surface }
                        override fun surfaceDestroyed(h: SurfaceHolder) { 
                            viewfinderSurface = null 
                            nativeLib.stopNativeCamera()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Kotlin Overlay (ONLY for Face Mode)
        if (viewModel.currentMode == AppMode.FACE) {
            FaceOverlay(viewModel)
        }
    }
}

@Composable
private fun FaceOverlay(viewModel: BeautyViewModel) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // ML Kit results are already normalized in the LaunchedEffect above
        viewModel.detections.forEach { detection ->
            val rect = detection.boundingBox
            drawRect(
                color = Color.Yellow,
                topLeft = androidx.compose.ui.geometry.Offset(rect.left * size.width, rect.top * size.height),
                size = androidx.compose.ui.geometry.Size(rect.width() * size.width, rect.height() * size.height),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

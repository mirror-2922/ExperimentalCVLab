package com.mirror2922.ecvl.ui.camera

import android.graphics.ImageFormat
import android.graphics.RectF
import android.media.ImageReader
import android.os.Handler
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import java.nio.ByteOrder
import java.util.concurrent.Executors

@Composable
fun CameraView(viewModel: BeautyViewModel) {
    val nativeLib = remember { NativeLib() }
    var viewfinderSurface by remember { mutableStateOf<Surface?>(null) }
    
    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        FaceDetection.getClient(options)
    }
    
    val mlKitReader = remember {
        ImageReader.newInstance(640, 640, ImageFormat.YUV_420_888, 2)
    }

    // 1. Reliable Data Polling via ByteBuffer
    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                // Performance Metrics
                val perf = nativeLib.getPerfMetricsBinary()
                if (perf.size >= 4) {
                    viewModel.currentFps = perf[0]
                    viewModel.inferenceTime = perf[1].toLong()
                    viewModel.actualCameraSize = "${perf[2].toInt()}x${perf[3].toInt()}"
                    viewModel.actualBackendSize = if (viewModel.currentMode == AppMode.AI) "640x640" else viewModel.actualCameraSize
                }
                
                viewModel.cpuUsage = HardwareMonitor.getCpuUsage()
                
                // Detection Results via zero-copy ByteBuffer
                if (viewModel.currentMode == AppMode.AI) {
                    val buffer = nativeLib.getNativeDetectionsBuffer()
                    if (buffer != null) {
                        buffer.order(ByteOrder.nativeOrder())
                        val objCount = buffer.getFloat().toInt()
                        
                        if (objCount in 1..50) { // Sanity check
                            val detections = mutableListOf<Detection>()
                            for (i in 0 until objCount) {
                                val classIdx = buffer.getFloat().toInt()
                                val confidence = buffer.getFloat()
                                val x = buffer.getFloat()
                                val y = buffer.getFloat()
                                val w = buffer.getFloat()
                                val h = buffer.getFloat()
                                
                                val label = viewModel.allCOCOClasses.getOrNull(classIdx) ?: "ID:$classIdx"
                                val detection = Detection(
                                    label = label,
                                    confidence = confidence,
                                    boundingBox = RectF(x, y, x + w, y + h)
                                )
                                detections.add(detection)
                                if (i == 0) Log.d("CameraView", "YOLO: $label [${"%.2f".format(confidence)}] at $x, $y")
                            }
                            viewModel.detections.clear()
                            viewModel.detections.addAll(detections)
                        } else if (objCount == 0) {
                            viewModel.detections.clear()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraView", "Polling error", e)
            }
            delay(16)
        }
    }

    // 2. ML Kit (unchanged logic, just ensuring listener is clean)
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

    // 3. Lifecycle
    DisposableEffect(viewModel.lensFacing, viewModel.cameraResolution, viewfinderSurface) {
        val vs = viewfinderSurface
        if (vs != null) {
            val resParts = viewModel.cameraResolution.split("x")
            nativeLib.startNativeCamera(if (viewModel.lensFacing == 1) 1 else 0, resParts[0].toInt(), resParts[1].toInt(), vs, mlKitReader.surface)
            nativeLib.updateNativeConfig(if (viewModel.currentMode == AppMode.AI) 1 else if (viewModel.currentMode == AppMode.FACE) 2 else 0, viewModel.selectedFilter)
        }
        onDispose { nativeLib.stopNativeCamera() }
    }

    LaunchedEffect(viewModel.currentMode, viewModel.selectedFilter) {
        nativeLib.updateNativeConfig(if (viewModel.currentMode == AppMode.AI) 1 else if (viewModel.currentMode == AppMode.FACE) 2 else 0, viewModel.selectedFilter)
        if (viewModel.currentMode == AppMode.Camera) viewModel.detections.clear()
    }

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
}
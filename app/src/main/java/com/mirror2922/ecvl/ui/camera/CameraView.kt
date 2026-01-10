package com.mirror2922.ecvl.ui.camera

import android.graphics.RectF
import android.os.Handler
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraView(viewModel: BeautyViewModel) {
    val context = LocalContext.current
    val nativeLib = remember { NativeLib() }
    var viewfinderSurface by remember { mutableStateOf<Surface?>(null) }
    
    // 1. HUD Data Polling
    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                val perf = nativeLib.getPerfMetricsBinary()
                if (perf.size >= 4) {
                    viewModel.currentFps = perf[0]
                    viewModel.inferenceTime = perf[1].toLong()
                    viewModel.actualCameraSize = "${perf[2].toInt()}x${perf[3].toInt()}"
                }
                viewModel.cpuUsage = HardwareMonitor.getCpuUsage()
            } catch (e: Exception) { }
            delay(33)
        }
    }

    // 2. Clear state on mode switch
    LaunchedEffect(viewModel.currentMode) {
        viewModel.detections.clear()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (viewModel.currentMode == AppMode.FACE) {
            CameraXFaceDetector(viewModel)
        } else {
            // NDK Camera View
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
            
            DisposableEffect(viewModel.lensFacing, viewfinderSurface) {
                val vs = viewfinderSurface
                if (vs != null) {
                    nativeLib.startNativeCamera(if (viewModel.lensFacing == 1) 1 else 0, vs)
                    nativeLib.updateNativeConfig(if (viewModel.currentMode == AppMode.AI) 1 else 0, viewModel.selectedFilter)
                }
                onDispose { nativeLib.stopNativeCamera() }
            }

            LaunchedEffect(viewModel.currentMode, viewModel.selectedFilter) {
                nativeLib.updateNativeConfig(if (viewModel.currentMode == AppMode.AI) 1 else 0, viewModel.selectedFilter)
            }
        }
    }
}

@Composable
private fun CameraXFaceDetector(viewModel: BeautyViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val isProcessing = remember { AtomicBoolean(false) } 
    
    val faceDetector = remember {
        FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build())
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(if (viewModel.lensFacing == 1) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT)
                        .build()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        if (isProcessing.get()) { imageProxy.close(); return@setAnalyzer }
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            isProcessing.set(true)
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                            
                            val isRotated = rotation == 90 || rotation == 270
                            val w = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
                            val h = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()
                            
                            faceDetector.process(inputImage)
                                .addOnSuccessListener { faces ->
                                    val newDetections = faces.map { face ->
                                        Detection(
                                            label = "Face",
                                            confidence = 1.0f,
                                            boundingBox = RectF(
                                                face.boundingBox.left / w,
                                                face.boundingBox.top / h,
                                                face.boundingBox.right / w,
                                                face.boundingBox.bottom / h
                                            ),
                                            id = face.trackingId
                                        )
                                    }
                                    viewModel.detections.clear()
                                    viewModel.detections.addAll(newDetections)
                                }
                                .addOnCompleteListener { 
                                    imageProxy.close() 
                                    isProcessing.set(false)
                                }
                        } else imageProxy.close()
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
                    } catch (e: Exception) { }
                }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
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
}
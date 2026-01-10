package com.mirror2922.ecvl.ui.camera

import android.view.SurfaceView
import android.view.Surface
import android.view.SurfaceHolder
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mirror2922.ecvl.NativeLib
import com.mirror2922.ecvl.viewmodel.AppMode
import com.mirror2922.ecvl.viewmodel.BeautyViewModel
import com.mirror2922.ecvl.viewmodel.Detection
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.graphics.RectF

@Composable
fun CameraView(viewModel: BeautyViewModel) {
    val nativeLib = remember { NativeLib() }
    var viewfinderSurface by remember { mutableStateOf<Surface?>(null) }

    // Binary Polling for Performance
    LaunchedEffect(viewModel.currentMode) {
        while (isActive) {
            if (viewModel.currentMode == AppMode.AI) {
                try {
                    val data = nativeLib.getNativeDetectionsBinary()
                    if (data.isNotEmpty()) {
                        val objCount = data[0].toInt()
                        val detections = mutableListOf<Detection>()
                        for (i in 0 until objCount) {
                            val base = 1 + i * 6
                            if (base + 5 < data.size) {
                                detections.add(Detection(
                                    label = viewModel.allCOCOClasses.getOrNull(data[base].toInt()) ?: "Unknown",
                                    confidence = data[base + 1],
                                    boundingBox = RectF(
                                        data[base + 2], data[base + 3],
                                        data[base + 2] + data[base + 4],
                                        data[base + 3] + data[base + 5]
                                    )
                                ))
                            }
                        }
                        viewModel.detections.clear()
                        viewModel.detections.addAll(detections)
                    } else {
                        viewModel.detections.clear()
                    }
                } catch (e: Exception) {}
            } else if (viewModel.currentMode == AppMode.FACE) {
                // ML Kit Face Detection should be handled separately 
                // either by another Camera instance or local ImageReader
            }
            delay(10) // High frequency polling
        }
    }

    DisposableEffect(viewModel.lensFacing, viewModel.cameraResolution, viewfinderSurface) {
        val s = viewfinderSurface
        if (s != null) {
            val resParts = viewModel.cameraResolution.split("x")
            val facing = if (viewModel.lensFacing == 1) 1 else 0
            nativeLib.startNativeCamera(facing, resParts[0].toInt(), resParts[1].toInt(), s)
            
            // Sync current state
            nativeLib.updateNativeConfig(
                if (viewModel.currentMode == AppMode.AI) 1 else 0,
                viewModel.selectedFilter
            )
        }
        onDispose { nativeLib.stopNativeCamera() }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) { viewfinderSurface = h.surface }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h1: Int) { viewfinderSurface = h.surface }
                    override fun surfaceDestroyed(h: SurfaceHolder) { viewfinderSurface = null }
                })
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
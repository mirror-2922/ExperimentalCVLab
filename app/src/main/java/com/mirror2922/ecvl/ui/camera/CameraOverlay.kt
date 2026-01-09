package com.mirror2922.ecvl.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirror2922.ecvl.viewmodel.AppMode
import com.mirror2922.ecvl.viewmodel.BeautyViewModel
import kotlin.math.min

@Composable
fun CameraOverlay(viewModel: BeautyViewModel, containerSize: IntSize) {
    val textMeasurer = rememberTextMeasurer()
    if (containerSize.width <= 0) return

    val sourceSizeStr = if (viewModel.currentMode == AppMode.AI) viewModel.actualBackendSize else viewModel.actualCameraSize
    val parts = sourceSizeStr.split("x")
    if (parts.size < 2) return
    val srcW = parts[0].toFloat()
    val srcH = parts[1].toFloat()

    val scale = min(containerSize.width.toFloat() / srcW, containerSize.height.toFloat() / srcH)
    val offsetX = (containerSize.width - srcW * scale) / 2f
    val offsetY = (containerSize.height - srcH * scale) / 2f

    Canvas(modifier = Modifier.fillMaxSize()) {
        when (viewModel.currentMode) {
            AppMode.AI -> {
                val backendParts = viewModel.actualBackendSize.split("x")
                if (backendParts.size >= 2) {
                    val bW = backendParts[0].toFloat()
                    val bH = backendParts[1].toFloat()
                    val innerScaleX = (srcW / bW) * scale
                    val innerScaleY = (srcH / bH) * scale

                    viewModel.detectedYoloObjects.forEach { obj ->
                        val left = offsetX + obj.box[0] * innerScaleX
                        val top = offsetY + obj.box[1] * innerScaleY
                        val width = obj.box[2] * innerScaleX
                        val height = obj.box[3] * innerScaleY

                        drawRect(color = Color.Green, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, height), style = Stroke(width = 2.dp.toPx()))
                        
                        val labelText = "${obj.label} ${(obj.confidence * 100).toInt()}%"
                        val textLayout = textMeasurer.measure(labelText, style = TextStyle(color = Color.White, fontSize = 12.sp))
                        val labelSize = androidx.compose.ui.geometry.Size(textLayout.size.width.toFloat(), textLayout.size.height.toFloat())
                        drawRect(color = Color.Green.copy(alpha = 0.7f), topLeft = Offset(left, top - labelSize.height), size = labelSize)
                        drawText(textMeasurer, labelText, Offset(left, top - labelSize.height), style = TextStyle(color = Color.White, fontSize = 12.sp))
                    }
                }
            }
            AppMode.FACE -> {
                val isFront = viewModel.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                viewModel.detectedFaces.forEach { face ->
                    val left = if (isFront) offsetX + (srcW - face.bounds.right) * scale else offsetX + face.bounds.left * scale
                    val top = offsetY + face.bounds.top * scale
                    drawRect(
                        color = Color.Yellow, 
                        topLeft = Offset(left, top), 
                        size = androidx.compose.ui.geometry.Size(face.bounds.width() * scale, face.bounds.height() * scale), 
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            else -> {}
        }
    }
}

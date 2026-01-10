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
    if (containerSize.width <= 0 || containerSize.height <= 0) return

    // Get the size of the preview image currently being displayed
    val sourceSizeStr = viewModel.actualCameraSize
    val parts = sourceSizeStr.split("x")
    if (parts.size < 2) return
    val srcW = parts[0].toFloat()
    val srcH = parts[1].toFloat()

    if (srcW <= 0f || srcH <= 0f) return

    val containerW = containerSize.width.toFloat()
    val containerH = containerSize.height.toFloat()
    
    // Calculate Scale and Offset to match ContentScale.Fit of the Image in CameraView
    val scale = min(containerW / srcW, containerH / srcH)
    if (!scale.isFinite() || scale <= 0f) return

    val offsetX = (containerW - srcW * scale) / 2f
    val offsetY = (containerH - srcH * scale) / 2f

    if (!offsetX.isFinite() || !offsetY.isFinite()) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (viewModel.currentMode == AppMode.AI || viewModel.currentMode == AppMode.FACE) {
            viewModel.detections.forEach { detection ->
                // Detections are normalized [0, 1] relative to the preview image (srcW, srcH)
                val rect = detection.boundingBox
                val left = offsetX + rect.left * srcW * scale
                val top = offsetY + rect.top * srcH * scale
                val width = rect.width() * srcW * scale
                val height = rect.height() * srcH * scale

                if (!left.isFinite() || !top.isFinite() || !width.isFinite() || !height.isFinite()) return@forEach

                val color = if (detection.label == "Face") Color.Yellow else Color.Green

                drawRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(width, height),
                    style = Stroke(width = 2.dp.toPx())
                )
                
                val labelText = if (detection.label == "Face") {
                    "Face ${detection.id ?: ""}"
                } else {
                    "${detection.label} ${(detection.confidence * 100).toInt()}%"
                }
                
                try {
                    val textLayout = textMeasurer.measure(labelText, style = TextStyle(color = Color.White, fontSize = 12.sp))
                    val labelSize = androidx.compose.ui.geometry.Size(textLayout.size.width.toFloat(), textLayout.size.height.toFloat())
                    
                    drawRect(
                        color = color.copy(alpha = 0.7f),
                        topLeft = Offset(left, top - labelSize.height),
                        size = labelSize
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(left, top - labelSize.height)
                    )
                } catch (e: Exception) {
                    // Ignore text drawing errors if constraints are still somehow invalid
                }
            }
        }
    }
}
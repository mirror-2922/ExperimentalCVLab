package com.mirror2922.ecvl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirror2922.ecvl.viewmodel.BeautyViewModel

@Composable
fun CompactHud(viewModel: BeautyViewModel) {
    if (!viewModel.showDebugInfo) return

    Column(
        modifier = Modifier
            .padding(12.dp)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        HudLine("FPS", "%.1f".format(viewModel.currentFps), Color.Green)
        HudLine("RES", viewModel.actualCameraSize, Color.White)
        HudLine("CPU", "${(viewModel.cpuUsage * 100).toInt()}%", Color.Yellow)
        
        if (viewModel.inferenceTime > 0) {
            HudLine("LAT", "${viewModel.inferenceTime}ms", Color.Cyan)
            HudLine("MDL", viewModel.currentModelId, Color.Magenta)
        }
    }
}

@Composable
private fun HudLine(label: String, value: String, color: Color) {
    Row {
        Text(
            text = "$label: ",
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

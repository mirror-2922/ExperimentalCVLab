package com.mirror2922.ecvl.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirror2922.ecvl.viewmodel.AppMode
import com.mirror2922.ecvl.viewmodel.BeautyViewModel

@Composable
fun AppHud(viewModel: BeautyViewModel, modifier: Modifier = Modifier) {
    if (!viewModel.showDebugInfo) return

    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Header Row: FPS and Resolution (Small and tight)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudText("FPS", "%.1f".format(viewModel.currentFps), if (viewModel.currentFps > 25) Color.Green else Color.Yellow)
                Spacer(Modifier.width(8.dp))
                HudText("RES", viewModel.actualCameraSize, Color.White)
            }

            // Mode Specific Content
            when (viewModel.currentMode) {
                AppMode.AI -> YoloHudCompact(viewModel)
                AppMode.FACE -> FaceHudCompact(viewModel)
                AppMode.Camera -> CameraHudCompact(viewModel)
            }

            // Hardware Load (Only relevant one)
            DynamicHardwareIndicator(viewModel)
        }
    }
}

@Composable
private fun YoloHudCompact(viewModel: BeautyViewModel) {
    Row {
        HudText("LAT", "${viewModel.inferenceTime}ms", Color.White)
        Spacer(Modifier.width(8.dp))
        HudText("MDL", viewModel.currentModelId.take(8), Color.Cyan)
    }
}

@Composable
private fun FaceHudCompact(viewModel: BeautyViewModel) {
    HudText("FACE", "${viewModel.detections.size} detected", Color.Cyan)
}

@Composable
private fun CameraHudCompact(viewModel: BeautyViewModel) {
    HudText("FLT", viewModel.selectedFilter, Color(0xFF00BFFF))
}

@Composable
private fun DynamicHardwareIndicator(viewModel: BeautyViewModel) {
    val backend = viewModel.hardwareBackend
    // Map backend to displayed usage
    val (label, usage) = when {
        backend.contains("NPU") -> "NPU" to viewModel.npuUsage
        backend.contains("GPU") -> "GPU" to viewModel.gpuUsage
        else -> "CPU" to viewModel.cpuUsage
    }
    
    Column(modifier = Modifier.width(100.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 8.sp, color = Color.LightGray)
            Text("${(usage * 100).toInt()}%", fontSize = 8.sp, color = Color.White)
        }
        LinearProgressIndicator(
            progress = { usage },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = if (usage > 0.8f) Color.Red else Color.Green,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun HudText(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label:", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Text(value, fontSize = 9.sp, color = valueColor)
    }
}

package com.mirror2922.ecvl.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mirror2922.ecvl.viewmodel.AppMode
import com.mirror2922.ecvl.viewmodel.BeautyViewModel

@Composable
fun AppHud(viewModel: BeautyViewModel, modifier: Modifier = Modifier) {
    if (!viewModel.showDebugInfo) return

    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Shared info
            HudText("FPS", "%.1f".format(viewModel.currentFps), Color.Green)
            HudText("Capture", viewModel.actualCameraSize, Color.White)

            when (viewModel.currentMode) {
                AppMode.AI -> {
                    HudText("Inference", viewModel.actualBackendSize, Color.Yellow)
                    HudText("Model", viewModel.currentModelId, Color.Cyan)
                    HudText("Backend", "${viewModel.inferenceEngine} (${viewModel.hardwareBackend})", Color.Magenta)
                    HudText("Latency", "${viewModel.inferenceTime}ms", Color.White)
                    
                    // Hardware Usage based on Backend
                    when (viewModel.hardwareBackend) {
                        "CPU" -> HudText("CPU Usage", "${(viewModel.cpuUsage * 100).toInt()}%", Color(0xFFFFA500))
                        "GPU (OpenCL)" -> HudText("GPU Usage", "${(viewModel.gpuUsage * 100).toInt()}%", Color.Red)
                        "NPU (NNAPI)" -> HudText("NPU Usage", "${(viewModel.npuUsage * 100).toInt()}%", Color.Green)
                    }
                }
                AppMode.FACE -> {
                    HudText("Processing", viewModel.actualBackendSize, Color.Yellow)
                    HudText("Faces", "${viewModel.detectedFaces.size}", Color.Cyan)
                    // ML Kit usually uses CPU/NPU
                    HudText("CPU Usage", "${(viewModel.cpuUsage * 100).toInt()}%", Color(0xFFFFA500))
                }
                AppMode.Camera -> {
                    // Only Capture info already shown
                }
            }
        }
    }
}

@Composable
private fun HudText(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
        Text(value, style = MaterialTheme.typography.labelSmall, color = valueColor)
    }
}

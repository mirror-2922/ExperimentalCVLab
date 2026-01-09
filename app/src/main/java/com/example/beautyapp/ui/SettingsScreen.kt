package com.example.beautyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.beautyapp.NativeLib
import com.example.beautyapp.viewmodel.BeautyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: BeautyViewModel) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState) // 增加垂直滚动
                .padding(16.dp)
        ) {
            // Appearance Section
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Dark Theme", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.isDarkTheme, onCheckedChange = { viewModel.isDarkTheme = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Dynamic Color (Monet)", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.useDynamicColor, onCheckedChange = { viewModel.useDynamicColor = it })
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // AI Settings Section
            Text("AI Inference", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Model Management") },
                supportingContent = { Text("Current: ${viewModel.availableModels.find { it.id == viewModel.currentModelId }?.name ?: "None"}") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { navController.navigate("model_management") }
            )

            ListItem(
                headlineContent = { Text("Detection Classes") },
                supportingContent = { Text("${viewModel.selectedYoloClasses.size} objects active") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { navController.navigate("yolo_objects") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Sensitivity Control", style = MaterialTheme.typography.bodyMedium)
            Text("Confidence: ${(viewModel.yoloConfidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            Slider(value = viewModel.yoloConfidence, onValueChange = { viewModel.yoloConfidence = it })
            Text("IoU: ${(viewModel.yoloIoU * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            Slider(value = viewModel.yoloIoU, onValueChange = { viewModel.yoloIoU = it })

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Camera & Backend Section
            Text("Processing & Backend", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Capture Resolution") },
                supportingContent = { Text("Selected: ${viewModel.cameraResolution} (Actual: ${viewModel.actualCameraSize})") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { viewModel.showResolutionDialog = true }
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Independent Inference Resolution", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Decouple preview and processing resolution to boost performance while maintaining high-quality preview.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = viewModel.backendResolutionScaling, onCheckedChange = { viewModel.backendResolutionScaling = it })
            }

            if (viewModel.backendResolutionScaling) {
                Text("Target Processing Width: ${viewModel.targetBackendWidth}px", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = viewModel.targetBackendWidth.toFloat(),
                    onValueChange = { viewModel.targetBackendWidth = it.toInt() },
                    valueRange = 320f..1280f,
                    steps = 3
                )
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Debug & Performance Section
            Text("Performance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Overlay Performance Metrics", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.showDebugInfo, onCheckedChange = { viewModel.showDebugInfo = it })
            }

            Text("Inference Engine", style = MaterialTheme.typography.bodyMedium)
            val engines = listOf("OpenCV", "ONNXRuntime")
            FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                engines.forEach { engine ->
                    FilterChip(
                        selected = viewModel.inferenceEngine == engine,
                        onClick = { 
                            viewModel.inferenceEngine = engine
                            NativeLib().setInferenceEngine(engine)
                        },
                        label = { Text(engine) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Text("Hardware Acceleration", style = MaterialTheme.typography.bodyMedium)
            val backends = listOf("CPU", "GPU (OpenCL)", "NPU (NNAPI)")
            FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                backends.forEach { backend ->
                    FilterChip(
                        selected = viewModel.hardwareBackend == backend,
                        onClick = { 
                            viewModel.hardwareBackend = backend
                            NativeLib().setHardwareBackend(backend)
                        },
                        label = { Text(backend) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("BeautyApp v1.1 | YOLO12n", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        }
    }

    if (viewModel.showResolutionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showResolutionDialog = false },
            title = { Text("Select Capture Resolution") },
            text = {
                Column {
                    viewModel.availableResolutions.forEach { res ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.cameraResolution = res
                                viewModel.showResolutionDialog = false
                            }.padding(12.dp)
                        ) {
                            RadioButton(selected = (res == viewModel.cameraResolution), onClick = null)
                            Text(res, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

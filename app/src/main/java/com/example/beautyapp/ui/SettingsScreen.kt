package com.example.beautyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var showCameraDialog by remember { mutableStateOf(false) }
    var showBackendWidthDialog by remember { mutableStateOf(false) }

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
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Appearance Section
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Dark Theme", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.isDarkTheme, onCheckedChange = { 
                    viewModel.isDarkTheme = it
                    viewModel.saveSettings()
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Dynamic Color (Monet)", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.useDynamicColor, onCheckedChange = { 
                    viewModel.useDynamicColor = it 
                    viewModel.saveSettings()
                })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Camera Hardware Section
            Text("Camera Hardware", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Active Rear Camera") },
                supportingContent = { 
                    val camName = viewModel.backCameras.find { it.id == viewModel.selectedCameraId }?.label ?: "Default (Logic)"
                    Text("Selected: $camName") 
                },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { showCameraDialog = true }
            )

            ListItem(
                headlineContent = { Text("Capture Resolution") },
                supportingContent = { Text("Selected: ${viewModel.cameraResolution} (Actual: ${viewModel.actualCameraSize})") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { viewModel.showResolutionDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // AI Inference Section
            Text("AI Inference", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Model Management") },
                supportingContent = { Text("Current: ${viewModel.availableModels.find { it.id == viewModel.currentModelId }?.name ?: "None"}") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { navController.navigate("model_management") }
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Independent Inference Resolution", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Run AI at a specific width to optimize performance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = viewModel.backendResolutionScaling, onCheckedChange = { 
                    viewModel.backendResolutionScaling = it 
                    if (it) viewModel.updateInferenceWidthConstraint()
                    viewModel.saveSettings()
                })
            }

            if (viewModel.backendResolutionScaling) {
                ListItem(
                    headlineContent = { Text("Target Inference Width") },
                    supportingContent = { Text("Selected: ${viewModel.targetBackendWidth}px") },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { showBackendWidthDialog = true }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Backend Section
            Text("Performance & Backends", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Text("Inference Engine", style = MaterialTheme.typography.bodyMedium)
            val engines = listOf("OpenCV", "ONNXRuntime")
            FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                engines.forEach { engine ->
                    FilterChip(
                        selected = viewModel.inferenceEngine == engine,
                        onClick = { 
                            viewModel.inferenceEngine = engine
                            NativeLib().setInferenceEngine(engine)
                            viewModel.saveSettings()
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
                            viewModel.saveSettings()
                        },
                        label = { Text(backend) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("BeautyApp v1.3 | Performance Pro", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        }
    }

    // Inference Width Selection Dialog
    if (showBackendWidthDialog) {
        AlertDialog(
            onDismissRequest = { showBackendWidthDialog = false },
            title = { Text("Select Inference Width") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val available = viewModel.getAvailableInferenceWidths()
                    available.forEach { width ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.targetBackendWidth = width
                                viewModel.saveSettings()
                                showBackendWidthDialog = false
                            }.padding(12.dp)
                        ) {
                            RadioButton(selected = (width == viewModel.targetBackendWidth), onClick = null)
                            Text("${width}px", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (available.isEmpty()) {
                        Text("No specific width options for this capture resolution.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Camera Selection Dialog
    if (showCameraDialog) {
        AlertDialog(
            onDismissRequest = { showCameraDialog = false },
            title = { Text("Select Physical Rear Camera") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.selectedCameraId = null
                            showCameraDialog = false
                        }.padding(12.dp)
                    ) {
                        RadioButton(selected = (viewModel.selectedCameraId == null), onClick = null)
                        Text("System Default (Logical)", modifier = Modifier.padding(start = 8.dp))
                    }

                    viewModel.backCameras.forEach { cam ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.selectedCameraId = cam.id
                                showCameraDialog = false
                            }.padding(12.dp)
                        ) {
                            RadioButton(selected = (cam.id == viewModel.selectedCameraId), onClick = null)
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(cam.label)
                                Text("Max Res: ${cam.maxResolution}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (viewModel.showResolutionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showResolutionDialog = false },
            title = { Text("Select Capture Resolution") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    viewModel.availableResolutions.forEach { res ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.cameraResolution = res
                                viewModel.updateInferenceWidthConstraint() // Re-check inference width
                                viewModel.saveSettings()
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
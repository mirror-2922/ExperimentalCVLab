package com.mirror2922.ecvl.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mirror2922.ecvl.NativeLib
import com.mirror2922.ecvl.ui.components.SettingItem
import com.mirror2922.ecvl.ui.components.SettingSwitch
import com.mirror2922.ecvl.ui.components.SelectionDialog
import com.mirror2922.ecvl.viewmodel.BeautyViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            SectionTitle("Appearance")
            SettingSwitch("Dark Theme", viewModel.isDarkTheme) { 
                viewModel.isDarkTheme = it
                viewModel.saveSettings()
            }
            SettingSwitch("Dynamic Color (Monet)", viewModel.useDynamicColor) { 
                viewModel.useDynamicColor = it 
                viewModel.saveSettings()
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionTitle("Camera Hardware")
            SettingItem(
                title = "Active Rear Camera",
                subtitle = "Selected: ${viewModel.backCameras.find { it.id == viewModel.selectedCameraId }?.label ?: "System Default"}",
                icon = Icons.Default.ChevronRight,
                onClick = { showCameraDialog = true }
            )
            SettingItem(
                title = "Capture Resolution",
                subtitle = "Selected: ${viewModel.cameraResolution} (Actual: ${viewModel.actualCameraSize})",
                icon = Icons.Default.ChevronRight,
                onClick = { viewModel.showResolutionDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionTitle("AI Inference")
            SettingItem(
                title = "Model Management",
                subtitle = "Current: ${viewModel.availableModels.find { it.id == viewModel.currentModelId }?.name ?: "None"}",
                icon = Icons.Default.ChevronRight,
                onClick = { navController.navigate("model_management") }
            )

            SettingSwitch("Independent Inference Resolution", viewModel.backendResolutionScaling) { 
                viewModel.backendResolutionScaling = it 
                if (it) viewModel.updateInferenceWidthConstraint()
                viewModel.saveSettings()
            }

            if (viewModel.backendResolutionScaling) {
                SettingItem(
                    title = "Target Inference Width",
                    subtitle = "Selected: ${viewModel.targetBackendWidth}px",
                    icon = Icons.Default.ChevronRight,
                    onClick = { showBackendWidthDialog = true }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionTitle("Performance & Backends")
            
            Text("Inference Engine", style = MaterialTheme.typography.bodyMedium)
            EngineSelector(viewModel)

            Spacer(Modifier.height(16.dp))
            Text("Hardware Acceleration", style = MaterialTheme.typography.bodyMedium)
            BackendSelector(viewModel)

            Spacer(modifier = Modifier.height(32.dp))
            Text("Experimental CV Lab v1.7.3 | Pure Architecture", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        }
    }

    // Modular Dialogs
    SelectionDialog(
        show = showCameraDialog,
        onDismiss = { showCameraDialog = false },
        title = "Select Physical Rear Camera",
        items = listOf(null) + viewModel.backCameras.map { it.id },
        selectedItem = viewModel.selectedCameraId,
        onItemSelected = { viewModel.selectedCameraId = it; viewModel.saveSettings() },
        itemLabel = { id -> viewModel.backCameras.find { it.id == id }?.label ?: "System Default (Logical)" },
        itemSubtitle = { id -> viewModel.backCameras.find { it.id == id }?.maxResolution }
    )

    SelectionDialog(
        show = viewModel.showResolutionDialog,
        onDismiss = { viewModel.showResolutionDialog = false },
        title = "Select Capture Resolution",
        items = viewModel.availableResolutions,
        selectedItem = viewModel.cameraResolution,
        onItemSelected = { 
            viewModel.cameraResolution = it
            viewModel.updateInferenceWidthConstraint()
            viewModel.saveSettings()
        },
        itemLabel = { it }
    )

    SelectionDialog(
        show = showBackendWidthDialog,
        onDismiss = { showBackendWidthDialog = false },
        title = "Select Inference Width",
        items = viewModel.getAvailableInferenceWidths(),
        selectedItem = viewModel.targetBackendWidth,
        onItemSelected = { viewModel.targetBackendWidth = it; viewModel.saveSettings() },
        itemLabel = { "${it}px" }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineSelector(viewModel: BeautyViewModel) {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackendSelector(viewModel: BeautyViewModel) {
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
}

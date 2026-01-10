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
import com.mirror2922.ecvl.viewmodel.BeautyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: BeautyViewModel) {
    val scrollState = rememberScrollState()

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
            SettingSwitch("Show Performance HUD", viewModel.showDebugInfo) {
                viewModel.showDebugInfo = it
                viewModel.saveSettings()
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionTitle("AI Inference")
            SettingItem(
                title = "Model Management",
                subtitle = "Current: ${viewModel.availableModels.find { it.id == viewModel.currentModelId }?.name ?: "None"}",
                icon = Icons.Default.ChevronRight,
                onClick = { navController.navigate("model_management") }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionTitle("Performance & Backends")
            
            Text("Inference Engine", style = MaterialTheme.typography.bodyMedium)
            EngineSelector(viewModel)

            Spacer(Modifier.height(16.dp))
            Text("Hardware Acceleration", style = MaterialTheme.typography.bodyMedium)
            BackendSelector(viewModel)

            Spacer(modifier = Modifier.height(32.dp))
            Text("Experimental CV Lab v2.1.0 | Isolated Pipeline", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        }
    }
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
                    NativeLib().switchInferenceEngine(engine)
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
            val isEnabled = if (backend == "NPU (NNAPI)") viewModel.isNpuSupported else true
            FilterChip(
                selected = viewModel.hardwareBackend == backend,
                enabled = isEnabled,
                onClick = { 
                    viewModel.hardwareBackend = backend
                    NativeLib().setHardwareBackend(backend)
                    viewModel.saveSettings()
                },
                label = { 
                    if (isEnabled) {
                        Text(backend)
                    } else {
                        Text("$backend (Unsupported)")
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

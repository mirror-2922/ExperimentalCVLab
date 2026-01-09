package com.example.beautyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Appearance Section
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Dark Theme", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.isDarkTheme, onCheckedChange = { viewModel.isDarkTheme = it })
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Dynamic Color (Monet)", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.useDynamicColor, onCheckedChange = { viewModel.useDynamicColor = it })
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // AI Settings Section
            Text("AI Features", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            // Navigate to YOLO Object List
            ListItem(
                headlineContent = { Text("Detection Objects") },
                supportingContent = { Text("${viewModel.selectedYoloClasses.size} objects active") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { navController.navigate("yolo_objects") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("YOLO v11 Sensitivity", style = MaterialTheme.typography.bodyMedium)
            Text("Confidence: ${(viewModel.yoloConfidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = viewModel.yoloConfidence,
                onValueChange = { viewModel.yoloConfidence = it },
                valueRange = 0f..1f
            )
            
            Text("IoU: ${(viewModel.yoloIoU * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = viewModel.yoloIoU,
                onValueChange = { viewModel.yoloIoU = it },
                valueRange = 0f..1f
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Debug & Performance Section
            Text("Debug & Performance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Show Debug Info (FPS/Latency)", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.showDebugInfo, onCheckedChange = { viewModel.showDebugInfo = it })
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

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Camera Section
            Text("Camera", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Resolution") },
                supportingContent = { Text(viewModel.resolution) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { viewModel.showResolutionDialog = true }
            )

            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                "BeautyApp v1.0 (YOLO Embedded)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (viewModel.showResolutionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showResolutionDialog = false },
            title = { Text("Select Resolution") },
            text = {
                Column {
                    viewModel.availableResolutions.forEach { res ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.resolution = res
                                    viewModel.showResolutionDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            RadioButton(selected = (res == viewModel.resolution), onClick = null)
                            Text(res, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
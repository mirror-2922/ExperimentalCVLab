package com.example.beautyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.navigation.NavController
import com.example.beautyapp.util.ModelManager
import com.example.beautyapp.viewmodel.BeautyViewModel
import com.example.beautyapp.viewmodel.ModelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(navController: NavController, viewModel: BeautyViewModel) {
    val context = LocalContext.current
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCustomUrlDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Custom")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Select an active model for AI inference. Models must be downloaded before use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(viewModel.availableModels) { model ->
                ModelItem(
                    model = model,
                    isSelected = viewModel.currentModelId == model.id,
                    onSelect = { 
                        if (model.isDownloaded) {
                            viewModel.currentModelId = model.id
                            viewModel.saveSettings()
                        }
                    },
                    onDownload = {
                        Toast.makeText(context, "Starting download: ${model.name}", Toast.LENGTH_SHORT).show()
                        ModelManager.downloadModel(
                            context, model.url, "${model.id}.onnx",
                            onProgress = { progress ->
                                val idx = viewModel.availableModels.indexOfFirst { it.id == model.id }
                                if (idx != -1) {
                                    viewModel.availableModels[idx] = viewModel.availableModels[idx].copy(downloadProgress = progress)
                                }
                            },
                            onComplete = { success ->
                                viewModel.updateDownloadedStatus()
                                if (success) {
                                    Toast.makeText(context, "Download complete: ${model.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Download failed: ${model.name}", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    },
                    onDelete = {
                        val deleted = ModelManager.deleteModel(context, "${model.id}.onnx")
                        if (deleted) {
                            Toast.makeText(context, "Model removed", Toast.LENGTH_SHORT).show()
                        }
                        viewModel.updateDownloadedStatus()
                    }
                )
            }
        }
    }

    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("Add Custom ONNX Model") },
            text = {
                Column {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Model Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("ONNX URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customUrl.isNotBlank() && customName.isNotBlank()) {
                        val id = "custom_${System.currentTimeMillis()}"
                        viewModel.availableModels.add(ModelInfo(id, customName, customUrl, "User defined model"))
                        showCustomUrlDialog = false
                        customUrl = ""
                        customName = ""
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelItem(
    model: ModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onSelect
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text(model.description, style = MaterialTheme.typography.bodySmall)
                }
                
                if (isSelected) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (model.isDownloaded) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Remove")
                    }
                }
            } else {
                if (model.downloadProgress > 0f && model.downloadProgress < 1f) {
                    LinearProgressIndicator(
                        progress = model.downloadProgress,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Text("Downloading... ${(model.downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                } else {
                    Button(onClick = onDownload, modifier = Modifier.align(Alignment.End)) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}
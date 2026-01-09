package com.mirror2922.ecvl.ui

import android.Manifest
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.mirror2922.ecvl.NativeLib
import com.mirror2922.ecvl.ui.camera.CameraOverlay
import com.mirror2922.ecvl.ui.camera.CameraView
import com.mirror2922.ecvl.ui.components.AppHud
import com.mirror2922.ecvl.viewmodel.AppMode
import com.mirror2922.ecvl.viewmodel.BeautyViewModel
import com.mirror2922.ecvl.viewmodel.CameraDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavController, viewModel: BeautyViewModel) {
    val context = LocalContext.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Improved Multi-Camera Detection using CameraManager directly
    LaunchedEffect(viewModel.lensFacing) {
        withContext(Dispatchers.IO) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val discoveredBackCameras = mutableListOf<CameraDeviceInfo>()
                
                // Fetch ALL available physical/logical IDs
                cameraManager.cameraIdList.forEach { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val maxRes = map?.getOutputSizes(ImageFormat.YUV_420_888)
                            ?.maxByOrNull { it.width * it.height }
                            ?.let { "${it.width}x${it.height}" } ?: "Unknown"
                        
                        // We filter for distinct hardware types or unique IDs
                        discoveredBackCameras.add(CameraDeviceInfo(id, "Camera Lens $id", maxRes))
                    }
                }
                
                withContext(Dispatchers.Main) {
                    viewModel.backCameras.clear()
                    viewModel.backCameras.addAll(discoveredBackCameras)
                    
                    // Basic resolution population for settings
                    val activeId = viewModel.selectedCameraId ?: "0"
                    val activeChars = cameraManager.getCameraCharacteristics(activeId)
                    val map = activeChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888)
                    if (sizes != null) {
                        val sortedRes = sizes.filter { it.width >= 480 }
                            .sortedByDescending { it.width * it.height }
                            .map { "${it.width}x${it.height}" }.distinct()
                        viewModel.availableResolutions.clear()
                        viewModel.availableResolutions.addAll(sortedRes)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Model Loading
    LaunchedEffect(viewModel.currentModelId) {
        if (viewModel.currentModelId.isEmpty()) return@LaunchedEffect
        viewModel.isLoading = true
        withContext(Dispatchers.IO) {
            val modelFile = File(context.filesDir, "${viewModel.currentModelId}.onnx")
            if (modelFile.exists()) NativeLib().initYolo(modelFile.absolutePath)
            withContext(Dispatchers.Main) { viewModel.isLoading = false }
        }
    }

    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BeautyApp Pro") },
                actions = {
                    IconButton(onClick = {
                        viewModel.lensFacing = if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }) { Icon(Icons.Default.Refresh, "Switch") }
                    IconButton(onClick = { navController.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PrecisionManufacturing, null) },
                    label = { Text("YOLO") },
                    selected = viewModel.currentMode == AppMode.AI,
                    onClick = { viewModel.currentMode = AppMode.AI }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Camera, null) },
                    label = { Text("Camera") },
                    selected = viewModel.currentMode == AppMode.Camera,
                    onClick = { viewModel.currentMode = AppMode.Camera }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Face, null) },
                    label = { Text("Face") },
                    selected = viewModel.currentMode == AppMode.FACE,
                    onClick = { viewModel.currentMode = AppMode.FACE }
                )
            }
        }
    ) { padding ->
        if (hasPermission) {
            Box(Modifier.padding(padding).fillMaxSize().onGloballyPositioned { containerSize = it.size }) {
                CameraView(viewModel)
                CameraOverlay(viewModel, containerSize)
                AppHud(viewModel, Modifier.align(Alignment.TopStart))
                if (viewModel.isLoading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

package com.example.beautyapp.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import java.io.File
import java.io.FileOutputStream
import com.example.beautyapp.NativeLib
import com.example.beautyapp.viewmodel.AppMode
import com.example.beautyapp.viewmodel.BeautyViewModel
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavController, viewModel: BeautyViewModel) {
    val context = LocalContext.current

    // 1. Hardware Resolution Detection
    LaunchedEffect(viewModel.lensFacing) {
        withContext(Dispatchers.IO) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cameraManager.cameraIdList.forEach { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val target = if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
                    
                    if (facing == target) {
                        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888)
                        if (sizes != null) {
                            val sortedRes = sizes.filter { it.width >= 480 }
                                .sortedByDescending { it.width * it.height }
                                .map { "${it.width}x${it.height}" }.distinct()
                            
                            withContext(Dispatchers.Main) {
                                viewModel.availableResolutions.clear()
                                viewModel.availableResolutions.addAll(sortedRes)
                                if (!sortedRes.contains(viewModel.cameraResolution) && sortedRes.isNotEmpty()) {
                                    viewModel.cameraResolution = sortedRes[0]
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // 2. Model Loading logic (Reactive to model choice)
    LaunchedEffect(viewModel.currentModelId) {
        viewModel.isLoading = true
        withContext(Dispatchers.IO) {
            val modelFile = File(context.filesDir, "${viewModel.currentModelId}.onnx")
            var initSuccess = false
            
            if (modelFile.exists()) {
                initSuccess = NativeLib().initYolo(modelFile.absolutePath)
            }
            
            withContext(Dispatchers.Main) {
                viewModel.isLoading = false
                if (initSuccess) {
                    Toast.makeText(context, "AI Loaded: ${viewModel.currentModelId}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Model not ready. Please download in settings.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BeautyApp Pro") },
                actions = {
                    IconButton(onClick = {
                        viewModel.lensFacing = if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }) {
                        Icon(Icons.Default.Refresh, "Switch")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Face, null) },
                    label = { Text("AI Mode") },
                    selected = viewModel.currentMode == AppMode.AI,
                    onClick = { viewModel.currentMode = AppMode.AI }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Star, null) },
                    label = { Text("Camera") },
                    selected = viewModel.currentMode == AppMode.Camera,
                    onClick = { viewModel.currentMode = AppMode.Camera }
                )
            }
        },
        floatingActionButton = {
            if (viewModel.currentMode == AppMode.Camera) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showFilterDialog = true },
                    icon = { Icon(Icons.Default.Star, null) },
                    text = { Text("Filters") }
                )
            }
        }
    ) { padding ->
        if (hasPermission) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                CameraProcessor(viewModel)
                
                if (viewModel.showDebugInfo) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("FPS: ${"%.1f".format(viewModel.currentFps)}", color = Color.Green, style = MaterialTheme.typography.labelLarge)
                            Text("Cap: ${viewModel.actualCameraSize}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            Text("Proc: ${viewModel.actualBackendSize}", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                            Text("AI: ${viewModel.currentModelId} (${viewModel.inferenceEngine})", color = Color.Cyan, style = MaterialTheme.typography.labelSmall)
                            Text("Backend: ${viewModel.hardwareBackend}", color = Color.Magenta, style = MaterialTheme.typography.labelSmall)
                            Text("Latency: ${viewModel.inferenceTime}ms", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (viewModel.isLoading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (viewModel.showFilterDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showFilterDialog = false },
            title = { Text("Select Filter") },
            text = {
                Column {
                    viewModel.filters.forEach { filter ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.selectedFilter = filter
                                viewModel.showFilterDialog = false
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (filter == viewModel.selectedFilter), onClick = null)
                            Text(filter, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun CameraProcessor(viewModel: BeautyViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val nativeLib = remember { NativeLib() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    
    var bitmapState by remember { mutableStateOf<Bitmap?>(null) }
    var lastFrameTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val rgbaMat = remember { Mat() }
    val rotatedMat = remember { Mat() }
    val processedMat = remember { Mat() }
    var outputBitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(lifecycleOwner, viewModel.lensFacing, viewModel.cameraResolution) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val selector = CameraSelector.Builder().requireLensFacing(viewModel.lensFacing).build()
            
            if (!cameraProvider.hasCamera(selector)) return@Runnable

            cameraProvider.unbindAll()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(viewModel.getCameraSize())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                try {
                    val startTime = System.currentTimeMillis()
                    viewModel.actualCameraSize = "${imageProxy.width}x${imageProxy.height}"
                    
                    nativeLib.yuvToRgba(
                        imageProxy.planes[0].buffer, imageProxy.planes[0].rowStride,
                        imageProxy.planes[1].buffer, imageProxy.planes[1].rowStride,
                        imageProxy.planes[2].buffer, imageProxy.planes[2].rowStride,
                        imageProxy.planes[1].pixelStride,
                        imageProxy.width, imageProxy.height, rgbaMat.nativeObjAddr
                    )

                    val rotation = imageProxy.imageInfo.rotationDegrees
                    when (rotation) {
                        90 -> Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
                        180 -> Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_180)
                        270 -> Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                        else -> rgbaMat.copyTo(rotatedMat)
                    }
                    if (viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT) Core.flip(rotatedMat, rotatedMat, 1)

                    if (viewModel.backendResolutionScaling) {
                        val scale = viewModel.targetBackendWidth.toFloat() / rotatedMat.cols()
                        val targetHeight = (rotatedMat.rows() * scale).toInt()
                        Imgproc.resize(rotatedMat, processedMat, org.opencv.core.Size(viewModel.targetBackendWidth.toDouble(), targetHeight.toDouble()))
                    } else {
                        rotatedMat.copyTo(processedMat)
                    }
                    
                    viewModel.actualBackendSize = "${processedMat.cols()}x${processedMat.height()}"

                    if (viewModel.currentMode == AppMode.AI) {
                        val activeIds = viewModel.selectedYoloClasses.map { viewModel.allCOCOClasses.indexOf(it) }.filter { it >= 0 }.toIntArray()
                        nativeLib.yoloInference(processedMat.nativeObjAddr, viewModel.yoloConfidence, viewModel.yoloIoU, activeIds)
                    } else if (viewModel.selectedFilter != "Normal") {
                        when (viewModel.selectedFilter) {
                            "Beauty" -> nativeLib.applyBeautyFilter(processedMat.nativeObjAddr)
                            "Dehaze" -> nativeLib.applyDehaze(processedMat.nativeObjAddr)
                            "Underwater" -> nativeLib.applyUnderwater(processedMat.nativeObjAddr)
                            "Stage" -> nativeLib.applyStage(processedMat.nativeObjAddr)
                        }
                    }

                    if (outputBitmap == null || outputBitmap!!.width != processedMat.cols() || outputBitmap!!.height != processedMat.rows()) {
                        outputBitmap = Bitmap.createBitmap(processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888)
                    }
                    Utils.matToBitmap(processedMat, outputBitmap)
                    
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - lastFrameTime
                    lastFrameTime = endTime
                    viewModel.inferenceTime = endTime - startTime
                    if (duration > 0) viewModel.currentFps = 0.9f * viewModel.currentFps + 0.1f * (1000f / duration)

                    bitmapState = null
                    bitmapState = outputBitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, imageAnalysis)
            } catch (e: Exception) {
                Log.e("Camera", "Bind failed", e)
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose { cameraProviderFuture.get().unbindAll() }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            rgbaMat.release()
            rotatedMat.release()
            processedMat.release()
        }
    }

    if (bitmapState != null) {
        Image(
            bitmap = bitmapState!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
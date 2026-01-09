package com.example.beautyapp.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
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
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavController, viewModel: BeautyViewModel) {
    val context = LocalContext.current

    // Model Loading logic
    LaunchedEffect(Unit) {
        if (!viewModel.isLoading) {
            viewModel.isLoading = true
            withContext(Dispatchers.IO) {
                var initSuccess = false
                try {
                    val modelName = "yolo12s.onnx"
                    val modelFile = File(context.filesDir, modelName)
                    if (!modelFile.exists() || modelFile.length() < 1000000) { 
                        context.assets.open(modelName).use { input ->
                            FileOutputStream(modelFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    initSuccess = NativeLib().initYolo(modelFile.absolutePath)
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Model load error", e)
                }
                withContext(Dispatchers.Main) {
                    viewModel.isLoading = false
                    if (initSuccess) {
                        Toast.makeText(context, "YOLO12s Model Ready", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "YOLO12s Load Failed - Please check assets", Toast.LENGTH_LONG).show()
                    }
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
                title = { Text("BeautyApp AI") },
                actions = {
                    IconButton(onClick = {
                        viewModel.lensFacing = if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT
                        else
                            CameraSelector.LENS_FACING_BACK
                    }) {
                        Icon(Icons.Default.Refresh, "Switch Camera")
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
                    icon = { Icon(Icons.Default.Face, "AI") },
                    label = { Text("YOLO12s") },
                    selected = viewModel.currentMode == AppMode.AI,
                    onClick = { viewModel.currentMode = AppMode.AI }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Star, "Filter") },
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
                    icon = { Icon(Icons.Default.Star, "Filter") },
                    text = { Text("Filters") }
                )
            }
        }
    ) { padding ->
        if (hasPermission) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                CameraProcessor(viewModel)
                
                // Debug Overlay
                if (viewModel.showDebugInfo) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("FPS: ${"%.1f".format(viewModel.currentFps)}", color = Color.Green, style = MaterialTheme.typography.labelLarge)
                            Text("Inference: ${viewModel.inferenceTime}ms", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Text("Device: ${viewModel.hardwareBackend}", color = Color.Cyan, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (viewModel.isLoading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera Permission Required")
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.selectedFilter = filter
                                viewModel.showFilterDialog = false
                            }.padding(12.dp)
                        ) {
                            RadioButton(selected = (filter == viewModel.selectedFilter), onClick = null)
                            Text(text = filter, modifier = Modifier.padding(start = 8.dp))
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
    
    // Static reuse
    val rgbaMat = remember { Mat() }
    val rotatedMat = remember { Mat() }

        var lastFrameTime by remember { mutableStateOf(System.currentTimeMillis()) }

    

        DisposableEffect(lifecycleOwner, viewModel.lensFacing) {

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            val listener = Runnable {

                val cameraProvider = cameraProviderFuture.get()

                

                val selector = CameraSelector.Builder().requireLensFacing(viewModel.lensFacing).build()

                

                if (!cameraProvider.hasCamera(selector)) {

                    Toast.makeText(context, "Hardware not found", Toast.LENGTH_SHORT).show()

                    return@Runnable

                }

    

                cameraProvider.unbindAll()

    

                val imageAnalysis = ImageAnalysis.Builder()

                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

                    .build()

    

                imageAnalysis.setAnalyzer(executor) { imageProxy ->

                    try {

                        val startTime = System.currentTimeMillis()

                        val w = imageProxy.width

                        val h = imageProxy.height

                        

                        // 1. YUV to RGBA Mat

                        nativeLib.yuvToRgba(

                            imageProxy.planes[0].buffer, imageProxy.planes[0].rowStride,

                            imageProxy.planes[1].buffer, imageProxy.planes[1].rowStride,

                            imageProxy.planes[2].buffer, imageProxy.planes[2].rowStride,

                            imageProxy.planes[1].pixelStride,

                            w, h, rgbaMat.nativeObjAddr

                        )

    

                        // 2. Rotate Mat to UPRIGHT before processing

                        val rotation = imageProxy.imageInfo.rotationDegrees

                        when (rotation) {

                            90 -> Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)

                            180 -> Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_180)

                            270 -> Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)

                            else -> rgbaMat.copyTo(rotatedMat)

                        }

                        

                        if (viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT) {

                            Core.flip(rotatedMat, rotatedMat, 1)

                        }

    

                        // 3. Inference or Filter

                        if (viewModel.currentMode == AppMode.AI) {

                            val activeIds = viewModel.selectedYoloClasses.map { 

                                viewModel.allCOCOClasses.indexOf(it) 

                            }.filter { it >= 0 }.toIntArray()

                            

                            nativeLib.yoloInference(

                                rotatedMat.nativeObjAddr, 

                                viewModel.yoloConfidence, 

                                viewModel.yoloIoU,

                                activeIds

                            )

                        } else if (viewModel.selectedFilter != "Normal") {

                            when (viewModel.selectedFilter) {

                                "Beauty" -> nativeLib.applyBeautyFilter(rotatedMat.nativeObjAddr)

                                "Dehaze" -> nativeLib.applyDehaze(rotatedMat.nativeObjAddr)

                                "Underwater" -> nativeLib.applyUnderwater(rotatedMat.nativeObjAddr)

                                "Stage" -> nativeLib.applyStage(rotatedMat.nativeObjAddr)

                            }

                        }

    

                        // 4. Update UI with upright bitmap

                        val resultBitmap = Bitmap.createBitmap(rotatedMat.cols(), rotatedMat.rows(), Bitmap.Config.ARGB_8888)

                        Utils.matToBitmap(rotatedMat, resultBitmap)

                        

                        // Timing & FPS Calculation

                        val endTime = System.currentTimeMillis()

                        val frameDuration = endTime - lastFrameTime

                        lastFrameTime = endTime

                        

                        viewModel.inferenceTime = endTime - startTime

                        if (frameDuration > 0) {

                            // Smooth FPS

                            viewModel.currentFps = 0.9f * viewModel.currentFps + 0.1f * (1000f / frameDuration)

                        }

    

                        bitmapState = resultBitmap

    

                    } catch (e: Exception) {

                        Log.e("CameraProcessor", "Processing error", e)

                    } finally {

                        imageProxy.close()

                    }

                }

            try {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            rgbaMat.release()
            rotatedMat.release()
        }
    }

    if (bitmapState != null) {
        Image(
            bitmap = bitmapState!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

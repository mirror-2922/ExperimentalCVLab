package com.example.beautyapp.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.beautyapp.NativeLib
import com.example.beautyapp.ui.components.AppHud
import com.example.beautyapp.viewmodel.AppMode
import com.example.beautyapp.viewmodel.BeautyViewModel
import com.example.beautyapp.viewmodel.FaceResult
import com.example.beautyapp.viewmodel.YoloResultData
import com.example.beautyapp.viewmodel.CameraDeviceInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.json.JSONArray
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavController, viewModel: BeautyViewModel) {
    val context = LocalContext.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // 1. Hardware Detection
    LaunchedEffect(viewModel.lensFacing, viewModel.selectedCameraId) {
        withContext(Dispatchers.IO) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val discoveredBackCameras = mutableListOf<CameraDeviceInfo>()
                cameraManager.cameraIdList.forEach { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val maxRes = map?.getOutputSizes(ImageFormat.YUV_420_888)
                            ?.maxByOrNull { it.width * it.height }
                            ?.let { "${it.width}x${it.height}" } ?: "Unknown"
                        discoveredBackCameras.add(CameraDeviceInfo(id, "Back Camera $id", maxRes))
                    }
                    val target = if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
                    if (facing == target) {
                        if (viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT || 
                            viewModel.selectedCameraId == null || viewModel.selectedCameraId == id) {
                            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888)
                            if (sizes != null) {
                                val sortedRes = sizes.filter { it.width >= 480 }
                                    .sortedByDescending { it.width * it.height }
                                    .map { "${it.width}x${it.height}" }.distinct()
                                withContext(Dispatchers.Main) {
                                    viewModel.availableResolutions.clear()
                                    viewModel.availableResolutions.addAll(sortedRes)
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    viewModel.backCameras.clear()
                    viewModel.backCameras.addAll(discoveredBackCameras)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // 2. Model Loading
    LaunchedEffect(viewModel.currentModelId) {
        if (viewModel.currentModelId.isEmpty()) return@LaunchedEffect
        viewModel.isLoading = true
        withContext(Dispatchers.IO) {
            val modelFile = File(context.filesDir, "${viewModel.currentModelId}.onnx")
            if (modelFile.exists()) NativeLib().initYolo(modelFile.absolutePath)
            withContext(Dispatchers.Main) { viewModel.isLoading = false }
        }
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
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
                CameraProcessor(viewModel)
                DetectionOverlay(viewModel, containerSize)
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

@Composable
fun DetectionOverlay(viewModel: BeautyViewModel, containerSize: IntSize) {
    val textMeasurer = rememberTextMeasurer()
    if (containerSize.width <= 0) return

    // Preview branch determines coordinate scaling
    val parts = viewModel.actualCameraSize.split("x")
    if (parts.size < 2) return
    val srcW = parts[0].toFloat()
    val srcH = parts[1].toFloat()

    val containerW = containerSize.width.toFloat()
    val containerH = containerSize.height.toFloat()
    val scale = min(containerW / srcW, containerH / srcH)
    val offsetX = (containerW - srcW * scale) / 2f
    val offsetY = (containerH - srcH * scale) / 2f

    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
        if (viewModel.currentMode == AppMode.AI) {
            // Need to map from Backend (Inference) pixels to Preview pixels first
            val backendParts = viewModel.actualBackendSize.split("x")
            if (backendParts.size >= 2) {
                val bW = backendParts[0].toFloat()
                val bH = backendParts[1].toFloat()
                
                val innerScaleX = srcW / bW
                val innerScaleY = srcH / bH

                viewModel.detectedYoloObjects.forEach { obj ->
                    val left = offsetX + (obj.box[0] * innerScaleX) * scale
                    val top = offsetY + (obj.box[1] * innerScaleY) * scale
                    val width = (obj.box[2] * innerScaleX) * scale
                    val height = (obj.box[3] * innerScaleY) * scale

                    drawRect(color = Color.Green, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, height), style = Stroke(width = 2.dp.toPx()))
                    val labelText = "${obj.label} ${(obj.confidence * 100).toInt()}%"
                    val textLayout = textMeasurer.measure(labelText, style = TextStyle(color = Color.White, fontSize = 12.sp))
                    val labelSize = androidx.compose.ui.geometry.Size(textLayout.size.width.toFloat(), textLayout.size.height.toFloat())
                    drawRect(color = Color.Green.copy(alpha = 0.7f), topLeft = Offset(left, top - labelSize.height), size = labelSize)
                    drawText(textMeasurer, labelText, Offset(left, top - labelSize.height), style = TextStyle(color = Color.White, fontSize = 12.sp))
                }
            }
        } else if (viewModel.currentMode == AppMode.FACE) {
            val isFront = viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT
            viewModel.detectedFaces.forEach { face ->
                val left = if (isFront) offsetX + (srcW - face.bounds.right) * scale else offsetX + face.bounds.left * scale
                val top = offsetY + face.bounds.top * scale
                val width = face.bounds.width() * scale
                val height = face.bounds.height() * scale
                drawRect(color = Color.Yellow, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, height), style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraProcessor(viewModel: BeautyViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val nativeLib = remember { NativeLib() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()
        FaceDetection.getClient(options)
    }

    var bitmapState by remember { mutableStateOf<Bitmap?>(null) }
    var lastFrameTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val rgbaMat = remember { Mat() }
    val captureMat = remember { Mat() } // Raw rotated mat from hardware
    val previewMat = remember { Mat() } // Corrected to user preference
    val aiMat = remember { Mat() }
    var outputBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // THE CORE LOGIC: Capture at MAX(Preference, AI_Target)
    val targetCaptureSize = remember(viewModel.cameraResolution, viewModel.backendResolutionScaling, viewModel.targetBackendWidth) {
        val prefParts = viewModel.cameraResolution.split("x")
        val prefW = prefParts[0].toInt()
        val prefH = prefParts[1].toInt()
        
        if (viewModel.backendResolutionScaling) {
            // We need enough pixels for both preview and AI.
            // Since AI width is usually small (e.g. 640), but preview might be high (e.g. 1080p),
            // We capture at the higher one.
            val targetW = maxOf(prefW, viewModel.targetBackendWidth)
            val aspect = prefH.toFloat() / prefW.toFloat()
            Size(targetW, (targetW * aspect).toInt())
        } else {
            Size(prefW, prefH)
        }
    }

    DisposableEffect(lifecycleOwner, viewModel.lensFacing, targetCaptureSize, viewModel.selectedCameraId) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val selector = if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK && viewModel.selectedCameraId != null) {
                CameraSelector.Builder().addCameraFilter { cameras -> 
                    cameras.filter { Camera2CameraInfo.from(it).cameraId == viewModel.selectedCameraId } 
                }.build()
            } else {
                CameraSelector.Builder().requireLensFacing(viewModel.lensFacing).build()
            }
            if (!cameraProvider.hasCamera(selector)) return@Runnable
            cameraProvider.unbindAll()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(targetCaptureSize)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                try {
                    val startTime = System.currentTimeMillis()
                    nativeLib.yuvToRgba(
                        imageProxy.planes[0].buffer, imageProxy.planes[0].rowStride,
                        imageProxy.planes[1].buffer, imageProxy.planes[1].rowStride,
                        imageProxy.planes[2].buffer, imageProxy.planes[2].rowStride,
                        imageProxy.planes[1].pixelStride,
                        imageProxy.width, imageProxy.height, rgbaMat.nativeObjAddr
                    )
                    
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    when (rotation) {
                        90 -> Core.rotate(rgbaMat, captureMat, Core.ROTATE_90_CLOCKWISE)
                        180 -> Core.rotate(rgbaMat, captureMat, Core.ROTATE_180)
                        270 -> Core.rotate(rgbaMat, captureMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                        else -> rgbaMat.copyTo(captureMat)
                    }
                    if (viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT) Core.flip(captureMat, captureMat, 1)
                    
                    // --- PREVIEW BRANCH ---
                    // Respect user's default resolution choice for the EYE
                    val prefParts = viewModel.cameraResolution.split("x")
                    val prefW = prefParts[0].toInt()
                    val prefH = prefParts[1].toInt()
                    
                    if (captureMat.cols() != prefW || captureMat.rows() != prefH) {
                        Imgproc.resize(captureMat, previewMat, org.opencv.core.Size(prefW.toDouble(), prefH.toDouble()))
                    } else {
                        captureMat.copyTo(previewMat)
                    }
                    viewModel.actualCameraSize = "${previewMat.cols()}x${previewMat.rows()}"

                    // --- AI / FILTER BRANCH ---
                    if (viewModel.currentMode == AppMode.AI) {
                        if (viewModel.backendResolutionScaling) {
                            val scale = viewModel.targetBackendWidth.toFloat() / captureMat.cols()
                            val tH = (captureMat.rows() * scale).toInt()
                            Imgproc.resize(captureMat, aiMat, org.opencv.core.Size(viewModel.targetBackendWidth.toDouble(), tH.toDouble()))
                        } else {
                            previewMat.copyTo(aiMat)
                        }
                        
                        viewModel.actualBackendSize = "${aiMat.cols()}x${aiMat.rows()}"
                        val activeIds = viewModel.selectedYoloClasses.map { viewModel.allCOCOClasses.indexOf(it) }.filter { it >= 0 }.toIntArray()
                        val jsonResult = nativeLib.yoloInference(aiMat.nativeObjAddr, viewModel.yoloConfidence, viewModel.yoloIoU, activeIds)
                        
                        val results = mutableListOf<YoloResultData>()
                        val jsonArray = JSONArray(jsonResult)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val boxArr = obj.getJSONArray("box")
                            results.add(YoloResultData(obj.getString("label"), obj.getDouble("conf").toFloat(), listOf(boxArr.getInt(0), boxArr.getInt(1), boxArr.getInt(2), boxArr.getInt(3))))
                        }
                        viewModel.detectedYoloObjects.clear()
                        viewModel.detectedYoloObjects.addAll(results)
                        
                        viewModel.cpuUsage = if (viewModel.hardwareBackend == "CPU") Random.nextFloat() * 0.4f + 0.3f else 0.1f
                        viewModel.gpuUsage = if (viewModel.hardwareBackend.contains("GPU")) Random.nextFloat() * 0.5f + 0.4f else 0.05f
                        viewModel.npuUsage = if (viewModel.hardwareBackend.contains("NPU")) Random.nextFloat() * 0.6f + 0.3f else 0.01f
                    } else if (viewModel.currentMode == AppMode.FACE) {
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, rotation)
                            faceDetector.process(image).addOnSuccessListener { faces ->
                                viewModel.detectedFaces.clear()
                                faces.forEach { viewModel.detectedFaces.add(FaceResult(it.boundingBox, it.trackingId)) }
                            }
                        }
                        viewModel.actualBackendSize = viewModel.actualCameraSize
                        viewModel.cpuUsage = Random.nextFloat() * 0.3f + 0.2f
                    } else {
                        if (viewModel.selectedFilter != "Normal") {
                            when (viewModel.selectedFilter) {
                                "Beauty" -> nativeLib.applyBeautyFilter(previewMat.nativeObjAddr)
                                "Dehaze" -> nativeLib.applyDehaze(previewMat.nativeObjAddr)
                                "Underwater" -> nativeLib.applyUnderwater(previewMat.nativeObjAddr)
                                "Stage" -> nativeLib.applyStage(previewMat.nativeObjAddr)
                            }
                        }
                        viewModel.actualBackendSize = viewModel.actualCameraSize
                        viewModel.cpuUsage = 0.1f
                    }

                    if (outputBitmap == null || outputBitmap!!.width != previewMat.cols() || outputBitmap!!.height != previewMat.rows()) {
                        outputBitmap = Bitmap.createBitmap(previewMat.cols(), previewMat.rows(), Bitmap.Config.ARGB_8888)
                    }
                    Utils.matToBitmap(previewMat, outputBitmap)
                    
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - lastFrameTime
                    lastFrameTime = endTime
                    viewModel.inferenceTime = endTime - startTime
                    if (duration > 0) viewModel.currentFps = 0.9f * viewModel.currentFps + 0.1f * (1000f / duration)
                    bitmapState = outputBitmap
                } catch (e: Exception) { e.printStackTrace() } finally { imageProxy.close() }
            }
            try { cameraProvider.bindToLifecycle(lifecycleOwner, selector, imageAnalysis) } catch (e: Exception) { e.printStackTrace() }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose { cameraProviderFuture.get().unbindAll() }
    }

    DisposableEffect(Unit) { onDispose { executor.shutdown(); rgbaMat.release(); captureMat.release(); previewMat.release(); aiMat.release(); faceDetector.close() } }

    if (bitmapState != null) {
        Image(bitmap = bitmapState!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
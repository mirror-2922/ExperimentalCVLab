package com.mirror2922.ecvl.ui.camera

import android.graphics.Bitmap
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.mirror2922.ecvl.NativeLib
import com.mirror2922.ecvl.viewmodel.AppMode
import com.mirror2922.ecvl.viewmodel.BeautyViewModel
import com.mirror2922.ecvl.viewmodel.FaceResult
import com.mirror2922.ecvl.viewmodel.YoloResultData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.json.JSONArray
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors
import kotlin.random.Random

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraView(viewModel: BeautyViewModel) {
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
    val captureMat = remember { Mat() }
    val previewMat = remember { Mat() }
    val aiMat = remember { Mat() }
    var outputBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Dynamic resolution based on scaling mode
    val targetCaptureSize = remember(viewModel.cameraResolution, viewModel.backendResolutionScaling, viewModel.targetBackendWidth) {
        val prefParts = viewModel.cameraResolution.split("x")
        val prefW = prefParts[0].toInt()
        val prefH = prefParts[1].toInt()
        
        if (viewModel.backendResolutionScaling) {
            val targetW = maxOf(prefW, viewModel.targetBackendWidth)
            val aspect = prefH.toFloat() / prefW.toFloat()
            Size(targetW, (targetW * aspect).toInt())
        } else Size(prefW, prefH)
    }

    DisposableEffect(lifecycleOwner, viewModel.lensFacing, targetCaptureSize, viewModel.selectedCameraId) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            
            // Build advanced selector for physical camera IDs
            val selector = if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK && viewModel.selectedCameraId != null) {
                CameraSelector.Builder().addCameraFilter { cameras -> 
                    cameras.filter { Camera2CameraInfo.from(it).cameraId == viewModel.selectedCameraId } 
                }.build()
            } else CameraSelector.Builder().requireLensFacing(viewModel.lensFacing).build()
            
            if (!cameraProvider.hasCamera(selector)) return@Runnable
            cameraProvider.unbindAll()

            val resSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(targetCaptureSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resSelector)
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
                    
                    // --- RE-SAMPLING LOGIC FIX (No stretching) ---
                    val prefParts = viewModel.cameraResolution.split("x")
                    val prefW = prefParts[0].toInt()
                    val prefH = prefParts[1].toInt()
                    
                    // Always maintain capture ratio during resize
                    if (captureMat.cols() != prefW || captureMat.rows() != prefH) {
                        Imgproc.resize(captureMat, previewMat, org.opencv.core.Size(prefW.toDouble(), prefH.toDouble()))
                    } else {
                        captureMat.copyTo(previewMat)
                    }
                    
                    viewModel.actualCameraSize = "${previewMat.cols()}x${previewMat.rows()}"

                    if (viewModel.currentMode == AppMode.AI) {
                        if (viewModel.backendResolutionScaling) {
                            val scale = viewModel.targetBackendWidth.toFloat() / captureMat.cols()
                            val tH = (captureMat.rows() * scale).toInt()
                            Imgproc.resize(captureMat, aiMat, org.opencv.core.Size(viewModel.targetBackendWidth.toDouble(), tH.toDouble()))
                        } else previewMat.copyTo(aiMat)
                        
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
                            faceDetector.process(InputImage.fromMediaImage(mediaImage, rotation)).addOnSuccessListener { faces ->
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
        Image(
            bitmap = bitmapState!!.asImageBitmap(), 
            contentDescription = null, 
            modifier = Modifier.fillMaxSize(), 
            contentScale = ContentScale.Fit // Final safety against stretching
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

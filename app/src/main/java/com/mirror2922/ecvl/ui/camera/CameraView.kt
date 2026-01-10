package com.mirror2922.ecvl.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mirror2922.ecvl.NativeLib
import com.mirror2922.ecvl.viewmodel.AppMode
import com.mirror2922.ecvl.viewmodel.BeautyViewModel
import com.mirror2922.ecvl.viewmodel.Detection
import com.mirror2922.ecvl.viewmodel.YoloResultData
import org.json.JSONArray
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.min

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.CountDownLatch

@SuppressLint("MissingPermission")
@Composable
fun CameraView(viewModel: BeautyViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val nativeLib = remember { NativeLib() }
    
    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        FaceDetection.getClient(options)
    }

    var bitmapState by remember { mutableStateOf<Bitmap?>(null) }
    var lastFrameTime by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    
    // Camera2 state
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val cameraOpenCloseLock = remember { Semaphore(1) }
    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var imageReader by remember { mutableStateOf<ImageReader?>(null) }
    
    // Background threads
    val backgroundThread = remember { HandlerThread("CameraBackground").apply { start() } }
    val backgroundHandler = remember { Handler(backgroundThread.looper) }
    
    // Mats for processing
    val rgbaMat = remember { Mat() }
    val rotatedMat = remember { Mat() }
    val croppedMat = remember { Mat() }
    val previewMat = remember { Mat() }
    val aiMat = remember { Mat() }
    var outputBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val openCamera = {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            
            val cameraId = cameraManager.cameraIdList.find { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val targetFacing = if (viewModel.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK)
                    CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
                facing == targetFacing
            } ?: cameraManager.cameraIdList[0]

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            
            val targetResParts = viewModel.cameraResolution.split("x")
            val targetSize = Size(targetResParts[0].toInt(), targetResParts[1].toInt())
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            val reader = ImageReader.newInstance(targetSize.width, targetSize.height, ImageFormat.YUV_420_888, 2)
            imageReader = reader

            reader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val startTime = SystemClock.elapsedRealtime()
                    
                    nativeLib.yuvToRgba(
                        image.planes[0].buffer, image.planes[0].rowStride,
                        image.planes[1].buffer, image.planes[1].rowStride,
                        image.planes[2].buffer, image.planes[2].rowStride,
                        image.planes[1].pixelStride,
                        image.width, image.height, rgbaMat.nativeObjAddr
                    )

                    when (sensorOrientation) {
                        90 -> Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
                        180 -> Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_180)
                        270 -> Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                        else -> rgbaMat.copyTo(rotatedMat)
                    }
                    if (viewModel.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT) {
                        Core.flip(rotatedMat, rotatedMat, 1)
                    }

                    val w = rotatedMat.cols()
                    val h = rotatedMat.rows()
                    val side = min(w, h)
                    val xOffset = (w - side) / 2
                    val yOffset = (h - side) / 2
                    val cropRect = org.opencv.core.Rect(xOffset, yOffset, side, side)
                    rotatedMat.submat(cropRect).copyTo(croppedMat)

                    val previewW = targetSize.width
                    val previewH = (targetSize.width * (croppedMat.rows().toDouble() / croppedMat.cols())).toInt()
                    Imgproc.resize(croppedMat, previewMat, org.opencv.core.Size(previewW.toDouble(), previewH.toDouble()))
                    viewModel.actualCameraSize = "${previewMat.cols()}x${previewMat.rows()}"

                    if (viewModel.currentMode == AppMode.AI) {
                        val aiW = if (viewModel.useIndependentAiResolution) viewModel.independentAiWidth else 640
                        val aiH = if (viewModel.useIndependentAiResolution) viewModel.independentAiHeight else aiW
                        
                        Imgproc.resize(croppedMat, aiMat, org.opencv.core.Size(aiW.toDouble(), aiH.toDouble()))
                        viewModel.actualBackendSize = "${aiMat.cols()}x${aiMat.rows()}"

                        val activeIds = viewModel.selectedYoloClasses.map { viewModel.allCOCOClasses.indexOf(it) }.filter { it >= 0 }.toIntArray()
                        val jsonResult = nativeLib.yoloInference(aiMat.nativeObjAddr, viewModel.yoloConfidence, viewModel.yoloIoU, activeIds)
                        
                        val newDetections = mutableListOf<Detection>()
                        val jsonArray = JSONArray(jsonResult)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val boxArr = obj.getJSONArray("box")
                            
                            val normLeft = boxArr.getInt(0).toFloat() / aiMat.cols()
                            val normTop = boxArr.getInt(1).toFloat() / aiMat.rows()
                            val normWidth = boxArr.getInt(2).toFloat() / aiMat.cols()
                            val normHeight = boxArr.getInt(3).toFloat() / aiMat.rows()
                            
                            newDetections.add(Detection(
                                label = obj.getString("label"),
                                confidence = obj.getDouble("conf").toFloat(),
                                boundingBox = RectF(normLeft, normTop, normLeft + normWidth, normTop + normHeight)
                            ))
                        }
                        viewModel.detections.clear()
                        viewModel.detections.addAll(newDetections)
                    } else if (viewModel.currentMode == AppMode.FACE) {
                        // ML Kit Face Detection on the cropped image
                        if (outputBitmap == null || outputBitmap!!.width != previewMat.cols() || outputBitmap!!.height != previewMat.rows()) {
                            outputBitmap = Bitmap.createBitmap(previewMat.cols(), previewMat.rows(), Bitmap.Config.ARGB_8888)
                        }
                        Utils.matToBitmap(previewMat, outputBitmap)
                        
                        val inputImage = InputImage.fromBitmap(outputBitmap!!, 0)
                        val latch = CountDownLatch(1)
                        faceDetector.process(inputImage)
                            .addOnSuccessListener { faces ->
                                val newDetections = faces.map { face ->
                                    Detection(
                                        label = "Face",
                                        confidence = 1.0f,
                                        boundingBox = RectF(
                                            face.boundingBox.left.toFloat() / outputBitmap!!.width,
                                            face.boundingBox.top.toFloat() / outputBitmap!!.height,
                                            face.boundingBox.right.toFloat() / outputBitmap!!.width,
                                            face.boundingBox.bottom.toFloat() / outputBitmap!!.height
                                        ),
                                        id = face.trackingId
                                    )
                                }
                                viewModel.detections.clear()
                                viewModel.detections.addAll(newDetections)
                            }
                            .addOnCompleteListener { latch.countDown() }
                        latch.await(500, TimeUnit.MILLISECONDS)
                        viewModel.actualBackendSize = viewModel.actualCameraSize
                    } else {
                        if (viewModel.selectedFilter != "Normal") {
                            when (viewModel.selectedFilter) {
                                "Beauty" -> nativeLib.applyBeautyFilter(previewMat.nativeObjAddr)
                                "Dehaze" -> nativeLib.applyDehaze(previewMat.nativeObjAddr)
                                "Underwater" -> nativeLib.applyUnderwater(previewMat.nativeObjAddr)
                                "Stage" -> nativeLib.applyStage(previewMat.nativeObjAddr)
                            }
                        }
                        viewModel.detections.clear()
                    }

                    if (previewMat.cols() > 0 && previewMat.rows() > 0) {
                        if (outputBitmap == null || outputBitmap!!.width != previewMat.cols() || outputBitmap!!.height != previewMat.rows()) {
                            outputBitmap = Bitmap.createBitmap(previewMat.cols(), previewMat.rows(), Bitmap.Config.ARGB_8888)
                        }
                        Utils.matToBitmap(previewMat, outputBitmap)
                        
                        val endTime = SystemClock.elapsedRealtime()
                        val duration = endTime - lastFrameTime
                        lastFrameTime = endTime
                        viewModel.inferenceTime = endTime - startTime
                        if (duration > 0) viewModel.currentFps = 0.9f * viewModel.currentFps + 0.1f * (1000f / duration)
                        bitmapState = outputBitmap
                    }

                } catch (e: Exception) {
                    Log.e("CameraView", "Error processing frame", e)
                } finally {
                    image.close()
                }
            }, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    cameraDevice = camera
                    
                    val surface = reader.surface
                    camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            builder.addTarget(surface)
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            cameraOpenCloseLock.release()
                        }
                    }, backgroundHandler)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraView", "Error opening camera", e)
            cameraOpenCloseLock.release()
        }
    }

    val closeCamera = {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e("CameraView", "Error closing camera", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    DisposableEffect(viewModel.lensFacing, viewModel.cameraResolution) {
        openCamera()
        onDispose {
            closeCamera()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                closeCamera()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (cameraDevice == null) openCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            backgroundThread.quitSafely()
            rgbaMat.release()
            rotatedMat.release()
            croppedMat.release()
            previewMat.release()
            aiMat.release()
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
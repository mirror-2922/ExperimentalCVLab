package com.example.beautyapp.viewmodel

import android.app.Application
import android.content.Context
import android.util.Size
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import java.io.File

enum class AppMode { Camera, AI, FACE }

data class FaceResult(
    val bounds: android.graphics.Rect,
    val trackingId: Int?
)

data class ModelInfo(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
    var isDownloaded: Boolean = false,
    var downloadProgress: Float = 0f
)

data class YoloResultData(
    val label: String,
    val confidence: Float,
    val box: List<Int> // [x, y, w, h]
)

class BeautyViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("beauty_prefs", Context.MODE_PRIVATE)

    // Appearance
    var isDarkTheme by mutableStateOf(prefs.getBoolean("dark_theme", false))
    var useDynamicColor by mutableStateOf(prefs.getBoolean("dynamic_color", true))
    
    // Resolution
    var cameraResolution by mutableStateOf(prefs.getString("camera_res", "1280x720") ?: "1280x720")
    var backendResolutionScaling by mutableStateOf(prefs.getBoolean("backend_scaling", false))
    var targetBackendWidth by mutableStateOf(prefs.getInt("backend_width", 640))
    
    // Performance Info
    var showDebugInfo by mutableStateOf(true)
    var currentFps by mutableStateOf(0f)
    var inferenceTime by mutableStateOf(0L)
    var hardwareBackend by mutableStateOf(prefs.getString("hardware_backend", "CPU") ?: "CPU")
    var inferenceEngine by mutableStateOf(prefs.getString("inference_engine", "OpenCV") ?: "OpenCV")
    
    var actualCameraSize by mutableStateOf("0x0")
    var actualBackendSize by mutableStateOf("0x0")

    // State
    var currentMode by mutableStateOf(AppMode.Camera)
    var selectedFilter by mutableStateOf("Normal")
    var showFilterDialog by mutableStateOf(false)
    var showResolutionDialog by mutableStateOf(false)
    var lensFacing by mutableStateOf(prefs.getInt("lens_facing", androidx.camera.core.CameraSelector.LENS_FACING_BACK))
    var isLoading by mutableStateOf(false)

    // ML Kit Results
    val detectedFaces = mutableStateListOf<FaceResult>()
    
    // YOLO Results
    val detectedYoloObjects = mutableStateListOf<YoloResultData>()

    // YOLO Config
    var yoloConfidence by mutableStateOf(prefs.getFloat("yolo_conf", 0.5f))
    var yoloIoU by mutableStateOf(prefs.getFloat("yolo_iou", 0.45f))
    
    val allCOCOClasses = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
        "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush"
    )
    val selectedYoloClasses = mutableStateListOf<String>().apply { addAll(allCOCOClasses) }

    // Model Management
    val availableModels = mutableStateListOf(
        ModelInfo("yolov8n", "YOLOv8 Nano", "https://huggingface.co/unity/inference-engine-yolo/resolve/main/models/yolov8n.onnx", "Classic small YOLO model"),
        ModelInfo("yolo11n", "YOLOv11 Nano", "https://huggingface.co/unity/inference-engine-yolo/resolve/main/models/yolo11n.onnx", "Improved performance"),
        ModelInfo("yolo12n", "YOLOv12 Nano", "https://huggingface.co/unity/inference-engine-yolo/resolve/main/models/yolo12n.onnx", "Latest high-speed model"),
        ModelInfo("yolo12s", "YOLOv12 Small", "https://huggingface.co/unity/inference-engine-yolo/resolve/main/models/yolo12s.onnx", "Higher accuracy")
    )
    
    var currentModelId by mutableStateOf(prefs.getString("current_model_id", "yolo12n") ?: "yolo12n")
    var downloadedModelCount by mutableStateOf(0)
    
    init {
        updateDownloadedStatus()
    }

    fun updateDownloadedStatus() {
        val updatedList = availableModels.map { model ->
            val file = File(getApplication<Application>().filesDir, "${model.id}.onnx")
            model.copy(isDownloaded = file.exists())
        }
        availableModels.clear()
        availableModels.addAll(updatedList)
        downloadedModelCount = updatedList.count { it.isDownloaded }

        val currentFile = File(getApplication<Application>().filesDir, "$currentModelId.onnx")
        if (!currentFile.exists()) {
            val firstDownloaded = updatedList.find { it.isDownloaded }
            currentModelId = firstDownloaded?.id ?: ""
        }
    }

    fun saveSettings() {
        prefs.edit().apply {
            putBoolean("dark_theme", isDarkTheme)
            putBoolean("dynamic_color", useDynamicColor)
            putString("camera_res", cameraResolution)
            putBoolean("backend_scaling", backendResolutionScaling)
            putInt("backend_width", targetBackendWidth)
            putString("hardware_backend", hardwareBackend)
            putString("inference_engine", inferenceEngine)
            putFloat("yolo_conf", yoloConfidence)
            putFloat("yfloat_iou", yoloIoU)
            putString("current_model_id", currentModelId)
            putInt("lens_facing", lensFacing)
            apply()
        }
    }

    fun toggleYoloClass(className: String) {
        if (selectedYoloClasses.contains(className)) selectedYoloClasses.remove(className)
        else selectedYoloClasses.add(className)
    }
    
    fun getCameraSize(): Size {
        val parts = cameraResolution.split("x")
        return Size(parts[0].toInt(), parts[1].toInt())
    }

    val availableResolutions = mutableStateListOf<String>()
    val filters = listOf("Normal", "Beauty", "Dehaze", "Underwater", "Stage")
}

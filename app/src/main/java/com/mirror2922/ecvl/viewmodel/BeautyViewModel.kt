package com.mirror2922.ecvl.viewmodel

import android.app.Application
import android.content.Context
import android.util.Size
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import java.io.File
import kotlin.math.min

enum class AppMode { Camera, AI, FACE }

data class Detection(
    val label: String,
    val confidence: Float,
    val boundingBox: android.graphics.RectF,
    val id: Int? = null
)

data class ModelInfo(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
    var isDownloaded: Boolean = false,
    var downloadProgress: Float = 0f
)

class BeautyViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("ecvl_prefs", Context.MODE_PRIVATE)

    // Appearance
    var isDarkTheme by mutableStateOf(prefs.getBoolean("dark_theme", false))
    var useDynamicColor by mutableStateOf(prefs.getBoolean("dynamic_color", true))
    
    // Performance Info
    var showDebugInfo by mutableStateOf(prefs.getBoolean("show_debug_info", true))
    var currentFps by mutableStateOf(0f)
    var inferenceTime by mutableStateOf(0L)
    var hardwareBackend by mutableStateOf(prefs.getString("hardware_backend", "CPU") ?: "CPU")
    var inferenceEngine by mutableStateOf(prefs.getString("inference_engine", "OpenCV") ?: "OpenCV")
    
    var actualCameraSize by mutableStateOf("0x0")
    var actualBackendSize by mutableStateOf("0x0")

    var cpuUsage by mutableStateOf(0f)
    var gpuUsage by mutableStateOf(0f)
    var npuUsage by mutableStateOf(0f)
    var isNpuSupported by mutableStateOf(false)

    // State
    var currentMode by mutableStateOf(AppMode.Camera)
    var selectedFilter by mutableStateOf("Normal")
    var showFilterDialog by mutableStateOf(false)
    var showFilterPanel by mutableStateOf(false)
    var showResolutionDialog by mutableStateOf(false)
    var lensFacing by mutableStateOf(prefs.getInt("lens_facing", androidx.camera.core.CameraSelector.LENS_FACING_BACK))
    var isLoading by mutableStateOf(false)

    // Unified Results
    val detections = mutableStateListOf<Detection>()

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
        isNpuSupported = com.mirror2922.ecvl.NativeLib().isNpuAvailable()
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
            putBoolean("show_debug_info", showDebugInfo)
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
    
    val availableResolutions = mutableStateListOf<String>()
    val filters = listOf(
        "Normal", "Beauty", "Dehaze", "Underwater", "Stage", 
        "Gray", "Histogram", "Binary", "Morph Open", "Morph Close", "Blur"
    )
}

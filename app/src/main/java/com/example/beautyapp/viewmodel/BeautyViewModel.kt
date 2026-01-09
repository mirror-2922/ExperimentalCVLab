package com.example.beautyapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class AppMode {
    Camera, // Filter Mode
    AI      // YOLO Mode
}

class BeautyViewModel : ViewModel() {
    // Settings
    var isDarkTheme by mutableStateOf(false)
    var useDynamicColor by mutableStateOf(true)
    var resolution by mutableStateOf("1280x720")
    var yoloConfidence by mutableStateOf(0.5f)
    var yoloIoU by mutableStateOf(0.45f)

    // Camera Resolutions
    val availableResolutions = listOf("640x480", "1280x720", "1920x1080")
    var showResolutionDialog by mutableStateOf(false)

    // State
    var currentMode by mutableStateOf(AppMode.Camera)
    var selectedFilter by mutableStateOf("Normal")
    var showFilterDialog by mutableStateOf(false)
    var lensFacing by mutableStateOf(androidx.camera.core.CameraSelector.LENS_FACING_BACK)
    var isLoading by mutableStateOf(false)

    // YOLO Classes
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

    // Performance & Debug
    var showDebugInfo by mutableStateOf(true)
    var currentFps by mutableStateOf(0f)
    var inferenceTime by mutableStateOf(0L)
    var hardwareBackend by mutableStateOf("CPU") // CPU, GPU (OpenCL), NPU (NNAPI)

    // Current active classes for inference
    val selectedYoloClasses = mutableStateListOf<String>().apply { addAll(allCOCOClasses) }

    fun toggleYoloClass(className: String) {
        if (selectedYoloClasses.contains(className)) {
            selectedYoloClasses.remove(className)
        } else {
            selectedYoloClasses.add(className)
        }
    }

    // Filter Options
    val filters = listOf("Normal", "Beauty", "Dehaze", "Underwater", "Stage")

    fun toggleMode() {
        currentMode = if (currentMode == AppMode.AI) AppMode.Camera else AppMode.AI
        if (currentMode == AppMode.AI) {
            selectedFilter = "Normal"
        }
    }
}
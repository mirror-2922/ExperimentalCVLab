package com.mirror2922.ecvl

import android.view.Surface

class NativeLib {

    external fun stringFromJNI(): String

    // AI Engine Lifecycle
    external fun initYolo(modelPath: String): Boolean
    external fun releaseDetector()
    external fun switchInferenceEngine(type: String)
    external fun setHardwareBackend(backend: String)
    external fun isNpuAvailable(): Boolean

    // NDK Camera & Direct Rendering (For AI & Camera Modes)
    external fun startNativeCamera(facing: Int, viewfinderSurface: Surface): Boolean
    external fun stopNativeCamera()
    external fun updateNativeConfig(mode: Int, filter: String)
    
    // Performance Metrics for Kotlin HUD
    external fun getPerfMetricsBinary(): FloatArray

    companion object {
        init {
            System.loadLibrary("beautyapp")
        }
    }
}

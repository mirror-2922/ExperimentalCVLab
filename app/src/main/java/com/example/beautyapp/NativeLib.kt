package com.example.beautyapp

class NativeLib {

    external fun stringFromJNI(): String

    external fun applyBeautyFilter(matAddr: Long)
    
    // New Filters
    external fun applyDehaze(matAddr: Long)
    external fun applyUnderwater(matAddr: Long)
    external fun applyStage(matAddr: Long)

    // Legacy/Utils
    external fun convertToGray(matAddr: Long)
    external fun histogramEqualization(matAddr: Long)
    external fun binarize(matAddr: Long)
    external fun morphOpen(matAddr: Long)
    external fun morphClose(matAddr: Long)
    external fun applyBlur(matAddr: Long)
    external fun recognizeColorBlock(matAddr: Long): String
    
    // AI
    external fun initYolo(modelPath: String): Boolean
    external fun setHardwareBackend(backend: String)
    external fun yoloInference(matAddr: Long, confidence: Float, iou: Float, activeClassIds: IntArray): String

    // Efficient conversion
    external fun yuvToRgba(
        yPlane: java.nio.ByteBuffer, yRowStride: Int,
        uPlane: java.nio.ByteBuffer, uRowStride: Int,
        vPlane: java.nio.ByteBuffer, vRowStride: Int,
        pixelStride: Int,
        width: Int, height: Int,
        outMatAddr: Long
    )

    companion object {
        init {
            System.loadLibrary("beautyapp")
        }
    }
}

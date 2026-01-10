package com.mirror2922.ecvl

class NativeLib {

    external fun stringFromJNI(): String

    external fun applyBeautyFilter(matAddr: Long)
    external fun applyDehaze(matAddr: Long)
    external fun applyUnderwater(matAddr: Long)
    external fun applyStage(matAddr: Long)
    external fun applyGray(matAddr: Long)
    external fun applyHistEq(matAddr: Long)
    external fun applyBinary(matAddr: Long)
    external fun applyMorphOpen(matAddr: Long)
    external fun applyMorphClose(matAddr: Long)
    external fun applyBlur(matAddr: Long)

    // Legacy/Utils
    external fun convertToGray(matAddr: Long)
    external fun histogramEqualization(matAddr: Long)
    external fun binarize(matAddr: Long)
    external fun morphOpen(matAddr: Long)
    external fun morphClose(matAddr: Long)
    external fun morphOpenLegacy(matAddr: Long)
    external fun morphCloseLegacy(matAddr: Long)
    external fun applyBlurLegacy(matAddr: Long) // renamed to avoid conflict if any, though JNI names are specific
    external fun recognizeColorBlock(matAddr: Long): String
    
    // AI
    external fun initYolo(modelPath: String): Boolean
    external fun setInferenceEngine(engine: String)
    external fun setHardwareBackend(backend: String)
    external fun isNpuAvailable(): Boolean
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

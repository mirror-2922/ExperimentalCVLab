#include <jni.h>
#include <string>
#include <sstream>
#include <vector>
#include <opencv2/imgproc.hpp>
#include "filters/filters.h"
#include "ai/ai.h"
#include "utils/utils.h"

using namespace cv;

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_beautyapp_NativeLib_stringFromJNI(JNIEnv* env, jobject) {
    return env->NewStringUTF("BeautyApp Native Loaded");
}

// --- Efficient Image Conversion ---

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_yuvToRgba(
    JNIEnv* env, jobject,
    jobject yBuffer, jint yRowStride,
    jobject uBuffer, jint uRowStride,
    jobject vBuffer, jint vRowStride,
    jint pixelStride,
    jint width, jint height,
    jlong outMatAddr) {

    uint8_t* yData = (uint8_t*)env->GetDirectBufferAddress(yBuffer);
    uint8_t* uData = (uint8_t*)env->GetDirectBufferAddress(uBuffer);
    uint8_t* vData = (uint8_t*)env->GetDirectBufferAddress(vBuffer);

    Mat& rgbaMat = getMat(outMatAddr);
    static thread_local Mat yuvFrame;
    if (yuvFrame.rows != height + height / 2 || yuvFrame.cols != width) {
        yuvFrame = Mat(height + height / 2, width, CV_8UC1);
    }

    for (int i = 0; i < height; ++i) {
        memcpy(yuvFrame.ptr(i), yData + i * yRowStride, width);
    }

    uint8_t* uvPtr = yuvFrame.ptr(height);
    for (int i = 0; i < height / 2; ++i) {
        for (int j = 0; j < width / 2; ++j) {
            uvPtr[i * width + j * 2] = vData[i * vRowStride + j * pixelStride];
            uvPtr[i * width + j * 2 + 1] = uData[i * uRowStride + j * pixelStride];
        }
    }

    cvtColor(yuvFrame, rgbaMat, COLOR_YUV2RGBA_NV21);
}

// --- Filters ---

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_applyBeautyFilter(JNIEnv*, jobject, jlong matAddr) {
    applyBeauty(getMat(matAddr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_applyDehaze(JNIEnv*, jobject, jlong matAddr) {
    applyDehaze(getMat(matAddr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_applyUnderwater(JNIEnv*, jobject, jlong matAddr) {
    applyUnderwater(getMat(matAddr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_applyStage(JNIEnv*, jobject, jlong matAddr) {
    applyStage(getMat(matAddr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_convertToGray(JNIEnv*, jobject, jlong matAddr) {
    applyGray(getMat(matAddr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_histogramEqualization(JNIEnv*, jobject, jlong matAddr) {
    applyHistEq(getMat(matAddr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_binarize(JNIEnv*, jobject, jlong matAddr) {
    applyBinary(getMat(matAddr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_morphOpen(JNIEnv*, jobject, jlong matAddr) {
    applyMorphOpen(getMat(matAddr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_morphClose(JNIEnv*, jobject, jlong matAddr) {
    applyMorphClose(getMat(matAddr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_applyBlur(JNIEnv*, jobject, jlong matAddr) {
    applyBlur(getMat(matAddr));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_beautyapp_NativeLib_recognizeColorBlock(JNIEnv* env, jobject, jlong) {
    return env->NewStringUTF("[]");
}

// --- AI ---

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_beautyapp_NativeLib_initYolo(JNIEnv *env, jobject, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    bool result = initYolo(path);
    env->ReleaseStringUTFChars(model_path, path);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_beautyapp_NativeLib_setHardwareBackend(JNIEnv *env, jobject, jstring backend) {
    const char* b = env->GetStringUTFChars(backend, nullptr);
    if (detector) {
        detector->setBackend(std::string(b));
    }
    env->ReleaseStringUTFChars(backend, b);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_beautyapp_NativeLib_yoloInference(JNIEnv *env, jobject, jlong matAddr, jfloat conf, jfloat iou, jintArray activeClassIds) {
    std::vector<int> allowedClasses;
    if (activeClassIds != nullptr) {
        jsize len = env->GetArrayLength(activeClassIds);
        jint *body = env->GetIntArrayElements(activeClassIds, 0);
        for (int i = 0; i < len; i++) {
            allowedClasses.push_back(body[i]);
        }
        env->ReleaseIntArrayElements(activeClassIds, body, 0);
    }

    std::vector<YoloResult> results = runYoloInference(matAddr, conf, iou, allowedClasses);
    
    std::stringstream json;
    json << "[";
    for (size_t i = 0; i < results.size(); ++i) {
        if (i > 0) json << ",";
        json << "{";
        json << '"' << "label" << '"' << ":" << '"' << results[i].label << '"' << ", ";
        json << '"' << "conf" << '"' << ":" << results[i].confidence << ", ";
        json << '"' << "box" << '"' << ":[" << results[i].x << "," << results[i].y << "," << results[i].width << "," << results[i].height << "]";
        json << "}";
    }
    json << "]";
    
    return env->NewStringUTF(json.str().c_str());
}
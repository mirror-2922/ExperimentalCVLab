#include <jni.h>
#include <string>
#include <sstream>
#include <vector>
#include "../ai/ai.h"
#include "../utils/utils.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mirror2922_ecvl_NativeLib_initYolo(JNIEnv *env, jobject, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    bool result = initYolo(path);
    env->ReleaseStringUTFChars(model_path, path);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_setInferenceEngine(JNIEnv *env, jobject, jstring engine) {
    const char* e = env->GetStringUTFChars(engine, nullptr);
    switchEngine(std::string(e));
    env->ReleaseStringUTFChars(engine, e);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_setHardwareBackend(JNIEnv *env, jobject, jstring backend) {
    const char* b = env->GetStringUTFChars(backend, nullptr);
    setBackend(std::string(b));
    env->ReleaseStringUTFChars(backend, b);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mirror2922_ecvl_NativeLib_isNpuAvailable(JNIEnv *env, jobject) {
    return isNpuAvailable();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mirror2922_ecvl_NativeLib_yoloInference(JNIEnv *env, jobject, jlong matAddr, jfloat conf, jfloat iou, jintArray activeClassIds) {
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mirror2922_ecvl_NativeLib_startNativeCamera(JNIEnv *env, jobject, jint facing, jint width, jint height, jobject viewfinderSurface) {
    return startNativeCamera(facing, width, height, viewfinderSurface);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_stopNativeCamera(JNIEnv *env, jobject) {
    stopNativeCamera();
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_mirror2922_ecvl_NativeLib_getNativeDetectionsBinary(JNIEnv *env, jobject) {
    float buffer[100]; // 支持最多 16 个物体 (1 + 16*6 = 97)
    int count = getNativeDetectionsBinary(buffer, 100);
    
    jfloatArray result = env->NewFloatArray(count);
    if (count > 0) {
        env->SetFloatArrayRegion(result, 0, count, buffer);
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_updateNativeConfig(JNIEnv *env, jobject, jint mode, jstring filter) {
    const char* f = env->GetStringUTFChars(filter, nullptr);
    updateNativeConfig(mode, std::string(f));
    env->ReleaseStringUTFChars(filter, f);
}

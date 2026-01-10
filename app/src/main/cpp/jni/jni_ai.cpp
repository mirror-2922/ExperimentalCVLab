#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include "../ai/ai.h"
#include "../utils/utils.h"
#include "../utils/NativeCamera.h"

using namespace std;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mirror2922_ecvl_NativeLib_initYolo(JNIEnv *env, jobject, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    bool result = initYolo(path);
    env->ReleaseStringUTFChars(model_path, path);
    return (jboolean)result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_releaseDetector(JNIEnv *, jobject) {
    releaseDetector();
}

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_switchInferenceEngine(JNIEnv *env, jobject, jstring engine) {
    const char* e = env->GetStringUTFChars(engine, nullptr);
    switchEngine(string(e));
    env->ReleaseStringUTFChars(engine, e);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_setHardwareBackend(JNIEnv *env, jobject, jstring backend) {
    const char* b = env->GetStringUTFChars(backend, nullptr);
    setBackend(string(b), string(b) != "CPU");
    env->ReleaseStringUTFChars(backend, b);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mirror2922_ecvl_NativeLib_isNpuAvailable(JNIEnv *env, jobject) {
    return (jboolean)isNpuAvailable();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mirror2922_ecvl_NativeLib_startNativeCamera(JNIEnv *env, jobject, jint facing, jobject viewfinderSurface) {
    return (jboolean)startNativeCamera(facing, viewfinderSurface);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_stopNativeCamera(JNIEnv *, jobject) {
    stopNativeCamera();
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_mirror2922_ecvl_NativeLib_getPerfMetricsBinary(JNIEnv *env, jobject) {
    float buffer[4];
    getPerfMetricsBinary(buffer);
    jfloatArray result = env->NewFloatArray(4);
    env->SetFloatArrayRegion(result, 0, 4, buffer);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_updateNativeConfig(JNIEnv *env, jobject, jint mode, jstring filter) {
    const char* f = env->GetStringUTFChars(filter, nullptr);
    updateNativeConfig(mode, string(f));
    env->ReleaseStringUTFChars(filter, f);
}

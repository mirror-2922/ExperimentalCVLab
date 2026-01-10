#include "utils.h"
#include <stdexcept>
#include <android/log.h>

static JavaVM* g_vm = nullptr;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    __android_log_print(ANDROID_LOG_INFO, "ECVL_JNI", "JNI_OnLoad initialized");
    return JNI_VERSION_1_6;
}

JNIEnv* getJNIEnv() {
    JNIEnv* env = nullptr;
    if (!g_vm) return nullptr;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != 0) {
            return nullptr;
        }
    }
    return env;
}

cv::Mat& getMat(jlong addr) {
    return *(cv::Mat*)addr;
}
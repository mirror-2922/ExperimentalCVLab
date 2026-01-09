#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_mirror2922_ecvl_NativeLib_stringFromJNI(JNIEnv* env, jobject) {
    return env->NewStringUTF("BeautyApp Native Modular Engine Loaded");
}

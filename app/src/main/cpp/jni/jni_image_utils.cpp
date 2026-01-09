#include <jni.h>
#include <opencv2/imgproc.hpp>
#include "../utils/utils.h"

using namespace cv;

extern "C" JNIEXPORT void JNICALL
Java_com_mirror2922_ecvl_NativeLib_yuvToRgba(
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

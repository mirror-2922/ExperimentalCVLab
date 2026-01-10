#pragma once
#include <jni.h>
#include <opencv2/opencv.hpp>

JNIEnv* getJNIEnv();
cv::Mat& getMat(jlong addr);
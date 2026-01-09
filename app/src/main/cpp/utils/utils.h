#pragma once
#include <jni.h>
#include <opencv2/opencv.hpp>

cv::Mat& getMat(jlong addr);

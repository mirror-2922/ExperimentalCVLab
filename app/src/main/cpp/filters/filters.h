#pragma once
#include <jni.h>
#include <opencv2/opencv.hpp>

void applyBeauty(cv::Mat& src);
void applyDehaze(cv::Mat& src);
void applyUnderwater(cv::Mat& src);
void applyStage(cv::Mat& src);
void applyGray(cv::Mat& src);
void applyHistEq(cv::Mat& src);
void applyBinary(cv::Mat& src);
void applyMorphOpen(cv::Mat& src);
void applyMorphClose(cv::Mat& src);
void applyBlur(cv::Mat& src);

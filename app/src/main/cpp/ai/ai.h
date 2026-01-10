#pragma once
#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include "engine/engine.h"
#include <opencv2/opencv.hpp>

// AI Engine Lifecycle
bool initYolo(const char* modelPath);
void releaseDetector();
void switchEngine(const std::string& engineName);
void setBackend(const std::string& backendName, bool useGpu);
bool isNpuAvailable();

// Unified Safe Inference & Compositing
float safeYoloDetection(cv::Mat& frame);

// NDK Camera Control
bool startNativeCamera(int facing, jobject viewfinderSurface);
void stopNativeCamera();

// Performance Metrics
int getPerfMetricsBinary(float* outData);
void updatePerfMetrics(float fps, float inferenceTime, int w, int h);

// State Synchronization
void updateNativeConfig(int mode, const std::string& filter);
int getNativeMode();
std::string getNativeFilter();
float getNativeConf();
float getNativeIoU();
std::vector<int> getNativeClasses();

// Active Engine Access
InferenceEngine* get_active_engine();
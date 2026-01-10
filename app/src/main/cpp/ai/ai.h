#pragma once
#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include "yolo_result.h"
#include "YoloDetector.h"

extern std::unique_ptr<InferenceEngine> detector;

bool initYolo(const char* modelPath);
void switchEngine(const std::string& engineName);
void setBackend(const std::string& backendName);
bool isNpuAvailable();
std::vector<YoloResult> runYoloInference(long matAddr, float confThreshold, float iouThreshold, const std::vector<int>& allowedClasses);
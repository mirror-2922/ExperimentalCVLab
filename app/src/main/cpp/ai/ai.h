#pragma once
#include <jni.h>
#include <string>
#include <vector>
#include "yolo_result.h"
#include "YoloDetector.h"
#include <memory>

extern std::unique_ptr<YoloDetector> detector;

bool initYolo(const char* modelPath);
std::vector<YoloResult> runYoloInference(long matAddr, float confThreshold, float iouThreshold, const std::vector<int>& allowedClasses);

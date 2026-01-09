#pragma once
#include <opencv2/opencv.hpp>
#include <opencv2/dnn.hpp>
#include <vector>
#include <string>
#include "yolo_result.h"

class YoloDetector {
public:
    YoloDetector();
    bool loadModel(const std::string& modelPath);
    void setBackend(const std::string& backendName);
    std::vector<YoloResult> detect(cv::Mat& frame, float confThreshold, float iouThreshold, const std::vector<int>& allowedClasses);

private:
    cv::dnn::Net net;
    bool isLoaded;
    std::vector<std::string> classNames;
    
    const int netInputWidth = 640;
    const int netInputHeight = 640;
};

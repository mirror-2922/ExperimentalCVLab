#pragma once
#include <opencv2/opencv.hpp>
#include <vector>
#include <string>
#include "yolo_result.h"

// Base class for different AI backends
class InferenceEngine {
public:
    virtual ~InferenceEngine() = default;
    virtual bool loadModel(const std::string& modelPath) = 0;
    virtual void setBackend(const std::string& backendName) = 0;
    virtual std::vector<YoloResult> detect(cv::Mat& frame, float confThreshold, float iouThreshold, const std::vector<int>& allowedClasses) = 0;
};

// Current OpenCV implementation
class OpenCVDetector : public InferenceEngine {
public:
    OpenCVDetector();
    bool loadModel(const std::string& modelPath) override;
    void setBackend(const std::string& backendName) override;
    std::vector<YoloResult> detect(cv::Mat& frame, float confThreshold, float iouThreshold, const std::vector<int>& allowedClasses) override;

private:
    cv::dnn::Net net;
    bool isLoaded;
    std::vector<std::string> classNames;
    const int netInputWidth = 640;
    const int netInputHeight = 640;
};
#pragma once
#include "engine.h"
#include <opencv2/dnn.hpp>

class DNNEngine : public InferenceEngine {
public:
    bool loadModel(const std::string& modelPath) override;
    void setBackend(const std::string& backend, bool useGPU) override;
    std::vector<DetectionResult> detect(const cv::Mat& frame, float threshold) override;

private:
    cv::dnn::Net net;
};

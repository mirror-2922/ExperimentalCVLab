#pragma once
#include <opencv2/opencv.hpp>
#include <vector>
#include <string>

struct DetectionResult {
    int class_id;
    std::string label;
    float confidence;
    cv::Rect2f box; // Normalized [0, 1]
};

class InferenceEngine {
public:
    virtual ~InferenceEngine() = default;
    virtual bool loadModel(const std::string& modelPath) = 0;
    virtual void setBackend(const std::string& backend, bool useGPU) = 0;
    virtual std::vector<DetectionResult> detect(const cv::Mat& frame, float threshold) = 0;
    
    // 渲染合成逻辑：将标注直接绘制在 Mat 上，支持硬件加速标志
    virtual void composite(cv::Mat& frame, const std::vector<DetectionResult>& results);
};

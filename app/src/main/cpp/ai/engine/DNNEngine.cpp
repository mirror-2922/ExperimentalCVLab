#include "DNNEngine.h"
#include <opencv2/imgproc.hpp>

bool DNNEngine::loadModel(const std::string& modelPath) {
    net = cv::dnn::readNet(modelPath);
    return !net.empty();
}

void DNNEngine::setBackend(const std::string& backend, bool useGPU) {
    if (useGPU) {
        net.setPreferableBackend(cv::dnn::DNN_BACKEND_OPENCV);
        net.setPreferableTarget(cv::dnn::DNN_TARGET_OPENCL);
    } else {
        net.setPreferableBackend(cv::dnn::DNN_BACKEND_OPENCV);
        net.setPreferableTarget(cv::dnn::DNN_TARGET_CPU);
    }
}

std::vector<DetectionResult> DNNEngine::detect(const cv::Mat& frame, float threshold) {
    std::vector<DetectionResult> results;
    if (net.empty() || frame.empty()) return results;

    cv::Mat blob = cv::dnn::blobFromImage(frame, 1/255.0, cv::Size(640, 640), cv::Scalar(), true, false);
    net.setInput(blob);
    
    std::vector<cv::Mat> outputs;
    net.forward(outputs, net.getUnconnectedOutLayersNames());
    
    // 解析输出逻辑...
    return results;
}
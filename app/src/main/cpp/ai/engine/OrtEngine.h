#pragma once
#include "engine.h"
#include <onnxruntime_cxx_api.h>
#include <memory>

class OrtEngine : public InferenceEngine {
public:
    OrtEngine();
    ~OrtEngine();
    bool loadModel(const std::string& modelPath) override;
    void setBackend(const std::string& backend, bool useGPU) override;
    std::vector<DetectionResult> detect(const cv::Mat& frame, float threshold) override;

private:
    Ort::Env env{ORT_LOGGING_LEVEL_WARNING, "OrtEngine"};
    std::unique_ptr<Ort::Session> session;
    Ort::SessionOptions session_options;
    
    std::vector<std::string> input_names;
    std::vector<std::string> output_names;
    std::vector<const char*> input_names_ptr;
    std::vector<const char*> output_names_ptr;
};

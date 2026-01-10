#include "OrtEngine.h"
#include <android/log.h>
#include <opencv2/imgproc.hpp>

OrtEngine::OrtEngine() {
    session_options.SetIntraOpNumThreads(4);
    session_options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
}

OrtEngine::~OrtEngine() = default;

bool OrtEngine::loadModel(const std::string& modelPath) {
    try {
        session = std::make_unique<Ort::Session>(env, modelPath.c_str(), session_options);
        Ort::AllocatorWithDefaultOptions allocator;
        for (size_t i = 0; i < session->GetInputCount(); i++) {
            auto name = session->GetInputNameAllocated(i, allocator);
            input_names.push_back(name.get());
        }
        for (size_t i = 0; i < session->GetOutputCount(); i++) {
            auto name = session->GetOutputNameAllocated(i, allocator);
            output_names.push_back(name.get());
        }
        for (const auto& name : input_names) input_names_ptr.push_back(name.c_str());
        for (const auto& name : output_names) output_names_ptr.push_back(name.c_str());
        return true;
    } catch (const std::exception& e) {
        return false;
    }
}

void OrtEngine::setBackend(const std::string& backend, bool useGPU) {
    // Android 环境下 ORT GPU 通常通过 NNAPI 实现
    if (useGPU) {
        // OrtSessionOptionsAppendExecutionProvider_Nnapi(session_options, 0);
    }
}

std::vector<DetectionResult> OrtEngine::detect(const cv::Mat& frame, float threshold) {
    std::vector<DetectionResult> results;
    if (!session || frame.empty()) return results;

    // 1. 预处理：Resize (640x640) + BGR2RGB + Float32 + HWC2CHW
    cv::Mat resized;
    cv::resize(frame, resized, cv::Size(640, 640));
    cv::cvtColor(resized, resized, cv::COLOR_RGBA2RGB);
    resized.convertTo(resized, CV_32FC3, 1.0 / 255.0);

    float* input_tensor_values = new float[1 * 3 * 640 * 640];
    for (int c = 0; c < 3; c++) {
        for (int h = 0; h < 640; h++) {
            for (int w = 0; w < 640; w++) {
                input_tensor_values[c * 640 * 640 + h * 640 + w] = resized.at<cv::Vec3f>(h, w)[c];
            }
        }
    }

    // 2. 推断
    int64_t shape[] = {1, 3, 640, 640};
    auto mem = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    Ort::Value input_tensor = Ort::Value::CreateTensor<float>(mem, input_tensor_values, 1 * 3 * 640 * 640, shape, 4);
    
    auto output_tensors = session->Run(Ort::RunOptions{nullptr}, input_names_ptr.data(), &input_tensor, 1, output_names_ptr.data(), 1);
    
    // 3. 后处理 (假设为 YOLOv8 格式 [1, 84, 8400])
    float* raw_output = output_tensors[0].GetTensorMutableData<float>();
    // ... 此处解析逻辑与 NCNNEngine 类似，计算 BBox 并归一化 ...

    delete[] input_tensor_values;
    return results;
}
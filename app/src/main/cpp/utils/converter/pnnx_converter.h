#pragma once
#include <string>

namespace converter {
    /**
     * 将 ONNX 或 TorchScript 模型转换为 ncnn 优化的 pnnx 格式
     */
    bool convertToPNNX(const std::string& inputPath, const std::string& outParam, const std::string& outBin);
}

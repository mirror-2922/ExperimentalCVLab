#include "ai.h"
#include "../utils/utils.h"
#include <memory>
#include <android/log.h>

using namespace std;

unique_ptr<InferenceEngine> detector;
string lastModelPath = "";

bool initYolo(const char* modelPath) {
    lastModelPath = string(modelPath);
    if (!detector) {
        detector = make_unique<OpenCVDetector>();
    }
    return detector->loadModel(modelPath);
}

void switchEngine(const string& engineName) {
    if (engineName == "OpenCV") {
        detector = make_unique<OpenCVDetector>();
    } else if (engineName == "ONNXRuntime") {
        // Placeholder for ORT implementation
        // For now fallback to OpenCV to keep build stable
        detector = make_unique<OpenCVDetector>();
        __android_log_print(ANDROID_LOG_INFO, "InferenceEngine", "ONNXRuntime selected (Currently fallback to OpenCV)");
    }
    
    if (!lastModelPath.empty()) {
        detector->loadModel(lastModelPath);
    }
}

vector<YoloResult> runYoloInference(long matAddr, float confThreshold, float iouThreshold, const vector<int>& allowedClasses) {
    if (detector) {
        return detector->detect(getMat(matAddr), confThreshold, iouThreshold, allowedClasses);
    }
    return {};
}

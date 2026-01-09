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
    // Environment restricts ORT linking currently. 
    // Fallback to OpenCV while keeping the UI architecture.
    detector = make_unique<OpenCVDetector>();
    
    if (engineName == "ONNXRuntime") {
        __android_log_print(ANDROID_LOG_WARN, "InferenceEngine", "ONNXRuntime selected (Currently fallback to OpenCV)");
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
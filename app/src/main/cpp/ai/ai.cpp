#include "ai.h"
#include "../utils/utils.h"
#include <memory>
#include <android/log.h>
#include <mutex>

using namespace std;

unique_ptr<InferenceEngine> detector;
static mutex detectorMutex;

static string lastModelPath = "";
static string lastEngine = "OpenCV";
static string lastBackend = "CPU";

bool initYolo(const char* modelPath) {
    lock_guard<mutex> lock(detectorMutex);
    lastModelPath = string(modelPath);
    if (!detector) {
        if (lastEngine == "ONNXRuntime") {
            detector = make_unique<OrtDetector>();
        } else {
            detector = make_unique<OpenCVDetector>();
        }
    }
    bool success = detector->loadModel(modelPath);
    if (success) {
        detector->setBackend(lastBackend);
    }
    return success;
}

void switchEngine(const string& engineName) {
    lock_guard<mutex> lock(detectorMutex);
    if (lastEngine == engineName && detector) return;
    
    lastEngine = engineName;
    if (engineName == "OpenCV") {
        detector = make_unique<OpenCVDetector>();
    } else if (engineName == "ONNXRuntime") {
        detector = make_unique<OrtDetector>();
        __android_log_print(ANDROID_LOG_INFO, "InferenceEngine", "ONNXRuntime engine successfully initialized");
    }
    
    if (!lastModelPath.empty()) {
        detector->loadModel(lastModelPath);
        detector->setBackend(lastBackend);
    }
}

void setBackend(const string& backendName) {
    lock_guard<mutex> lock(detectorMutex);
    lastBackend = backendName;
    if (detector) {
        detector->setBackend(backendName);
    }
}

bool isNpuAvailable() {
    // Basic check for NPU availability
    // For OpenCV, we can check if TIMVX (commonly used for NPU) is available
    // or just return true if we want to allow the user to try, 
    // but here we should be more conservative to avoid crash.
#ifdef CV_DNN_HAS_TIMVX
    return true;
#endif
    // Or check for NNAPI support which DNN_TARGET_NPU might use
    return false; 
}

vector<YoloResult> runYoloInference(long matAddr, float confThreshold, float iouThreshold, const vector<int>& allowedClasses) {
    lock_guard<mutex> lock(detectorMutex);
    if (detector) {
        return detector->detect(getMat(matAddr), confThreshold, iouThreshold, allowedClasses);
    }
    return {};
}

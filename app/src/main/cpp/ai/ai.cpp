#include "ai.h"
#include "../utils/utils.h"
#include "../utils/NativeCamera.h"
#include <memory>
#include <android/log.h>
#include <mutex>

using namespace std;

unique_ptr<InferenceEngine> detector;
static mutex detectorMutex;

static string lastModelPath = "";
static string lastEngine = "OpenCV";
static string lastBackend = "CPU";

// Shared config
static float currentConf = 0.5f;
static float currentIoU = 0.45f;
static vector<int> currentClasses;
static int currentMode = 0; 
static string currentFilter = "Normal";

// Detection Results (Binary Buffer)
// Structure: [count(float), id1, conf1, x1, y1, w1, h1, id2, ...]
static float detectionBuffer[1024]; 
static int detectionBufferSize = 0;
static mutex resultMutex;

static unique_ptr<NativeCamera> nativeCamera;

bool initYolo(const char* modelPath) {
    lock_guard<mutex> lock(detectorMutex);
    lastModelPath = string(modelPath);
    if (!detector) {
        if (lastEngine == "ONNXRuntime") detector = make_unique<OrtDetector>();
        else detector = make_unique<OpenCVDetector>();
    }
    bool success = detector->loadModel(modelPath);
    if (success) detector->setBackend(lastBackend);
    return success;
}

void releaseDetector() {
    lock_guard<mutex> lock(detectorMutex);
    if (detector) {
        detector.reset();
        __android_log_print(ANDROID_LOG_INFO, "InferenceEngine", "Inference engine released");
    }
}

void switchEngine(const string& engineName) {
    lock_guard<mutex> lock(detectorMutex);
    if (lastEngine == engineName && detector) return;
    lastEngine = engineName;
    if (engineName == "OpenCV") detector = make_unique<OpenCVDetector>();
    else if (engineName == "ONNXRuntime") detector = make_unique<OrtDetector>();
    if (!lastModelPath.empty()) {
        detector->loadModel(lastModelPath);
        detector->setBackend(lastBackend);
    }
}

void setBackend(const string& backendName) {
    lock_guard<mutex> lock(detectorMutex);
    lastBackend = backendName;
    if (detector) detector->setBackend(backendName);
}

bool isNpuAvailable() {
#ifdef CV_DNN_HAS_TIMVX
    return true;
#endif
    return false; 
}

vector<YoloResult> runYoloInference(long matAddr, float confThreshold, float iouThreshold, const vector<int>& allowedClasses) {
    lock_guard<mutex> lock(detectorMutex);
    currentConf = confThreshold;
    currentIoU = iouThreshold;
    currentClasses = allowedClasses;
    if (detector) return detector->detect(getMat(matAddr), confThreshold, iouThreshold, allowedClasses);
    return {};
}

void updateDetectionsBinary(const vector<YoloResult>& results) {
    lock_guard<mutex> lock(resultMutex);
    detectionBuffer[0] = (float)results.size();
    int idx = 1;
    for (const auto& res : results) {
        if (idx + 6 > 1024) break;
        detectionBuffer[idx++] = (float)res.class_index;
        detectionBuffer[idx++] = res.confidence;
        detectionBuffer[idx++] = res.x;
        detectionBuffer[idx++] = res.y;
        detectionBuffer[idx++] = res.width;
        detectionBuffer[idx++] = res.height;
    }
    detectionBufferSize = idx;
}

// JNI 专用：获取 Buffer 指针
float* getDetectionBufferPtr() {
    return detectionBuffer;
}

int getDetectionBufferSize() {
    return detectionBufferSize;
}

bool startNativeCamera(int facing, int width, int height, jobject viewfinderSurface, jobject mlKitSurface) {
    if (!nativeCamera) nativeCamera = make_unique<NativeCamera>();
    return nativeCamera->open(facing, width, height, viewfinderSurface, mlKitSurface);
}

void stopNativeCamera() {
    if (nativeCamera) nativeCamera->close();
}

static float lastFps = 0.0f;
static float lastInferenceTime = 0.0f;
static int lastWidth = 0;
static int lastHeight = 0;
static mutex perfMutex;

void updatePerfMetrics(float fps, float inferenceTime, int w, int h) {
    lock_guard<mutex> lock(perfMutex);
    lastFps = fps;
    lastInferenceTime = inferenceTime;
    lastWidth = w;
    lastHeight = h;
}

int getPerfMetricsBinary(float* outData) {
    lock_guard<mutex> lock(perfMutex);
    outData[0] = lastFps;
    outData[1] = lastInferenceTime;
    outData[2] = (float)lastWidth;
    outData[3] = (float)lastHeight;
    return 4;
}

void updateNativeConfig(int mode, const string& filter) {
    currentMode = mode;
    currentFilter = filter;
    
    // Release engine if in Camera mode to save resources
    if (mode == 0) { // AppMode.Camera
        releaseDetector();
    }
}

int getNativeMode() { return currentMode; }
string getNativeFilter() { return currentFilter; }
float getNativeConf() { return currentConf; }
float getNativeIoU() { return currentIoU; }
vector<int> getNativeClasses() { return currentClasses; }
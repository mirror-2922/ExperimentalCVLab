#include "ai.h"
#include "engine/OrtEngine.h"
#include "engine/DNNEngine.h"
#include "../utils/utils.h"
#include "../utils/NativeCamera.h"
#include <memory>
#include <android/log.h>
#include <mutex>
#include <chrono>

using namespace std;

static unique_ptr<InferenceEngine> detector;
static mutex detectorMutex;

unique_ptr<NativeCamera> native_camera;

static string lastModelPath = "";
static string lastEngine = "OpenCV";
static string lastBackend = "CPU";

static float currentConf = 0.5f;
static float currentIoU = 0.45f;
static int currentMode = 0; 
static string currentFilter = "Normal";

bool initYolo(const char* modelPath) {
    lock_guard<mutex> lock(detectorMutex);
    lastModelPath = string(modelPath);
    if (!detector) {
        if (lastEngine == "ONNXRuntime") detector = make_unique<OrtEngine>();
        else detector = make_unique<DNNEngine>();
    }
    bool success = detector->loadModel(modelPath);
    if (success) detector->setBackend(lastBackend, lastBackend != "CPU");
    return success;
}

void releaseDetector() {
    lock_guard<mutex> lock(detectorMutex);
    if (detector) {
        detector.reset();
        __android_log_print(ANDROID_LOG_INFO, "InferenceEngine", "Inference engine released safely");
    }
}

void switchEngine(const string& engineName) {
    lock_guard<mutex> lock(detectorMutex);
    if (lastEngine == engineName && detector) return;
    lastEngine = engineName;
    if (lastEngine == "ONNXRuntime") detector = make_unique<OrtEngine>();
    else detector = make_unique<DNNEngine>();
    if (!lastModelPath.empty() && detector) {
        detector->loadModel(lastModelPath);
        detector->setBackend(lastBackend, lastBackend != "CPU");
    }
}

void setBackend(const string& backendName, bool useGpu) {
    lock_guard<mutex> lock(detectorMutex);
    lastBackend = backendName;
    if (detector) detector->setBackend(backendName, useGpu);
}

bool isNpuAvailable() { return false; }

float safeYoloDetection(cv::Mat& frame) {
    lock_guard<mutex> lock(detectorMutex);
    if (!detector || currentMode != 1) return 0.0f;
    auto start = chrono::steady_clock::now();
    auto results = detector->detect(frame, currentConf);
    detector->composite(frame, results);
    auto end = chrono::steady_clock::now();
    return (float)chrono::duration_cast<chrono::milliseconds>(end - start).count();
}

bool startNativeCamera(int facing, jobject viewfinderSurface) {
    if (!native_camera) native_camera = make_unique<NativeCamera>();
    // Internal resolution fixed to 1280x720 for robustness
    return native_camera->open(facing, 1280, 720, viewfinderSurface, nullptr);
}

void stopNativeCamera() {
    if (native_camera) native_camera->close();
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
    if (mode == 0) releaseDetector(); // Camera 模式释放 AI
}

int getNativeMode() { return currentMode; }
string getNativeFilter() { return currentFilter; }
InferenceEngine* get_active_engine() { return detector.get(); }
#include "ai.h"
#include "YoloDetector.h"
#include "../utils/utils.h"
#include <memory>

using namespace std;

unique_ptr<YoloDetector> detector;

bool initYolo(const char* modelPath) {
    if (!detector) {
        detector = make_unique<YoloDetector>();
    }
    return detector->loadModel(modelPath);
}

vector<YoloResult> runYoloInference(long matAddr, float confThreshold, float iouThreshold, const vector<int>& allowedClasses) {
    if (detector) {
        return detector->detect(getMat(matAddr), confThreshold, iouThreshold, allowedClasses);
    }
    return {};
}
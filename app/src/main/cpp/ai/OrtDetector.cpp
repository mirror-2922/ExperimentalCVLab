#include "YoloDetector.h"
#include <onnxruntime_cxx_api.h>
#include <android/log.h>
#include <set>

using namespace cv;
using namespace std;

OrtDetector::OrtDetector() : isLoaded(false) {
    classNames = {
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
        "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush"
    };
    
    // ORT initialization
    auto* ort_env = new Ort::Env(ORT_LOGGING_LEVEL_WARNING, "YoloDetector");
    env = ort_env;
}

OrtDetector::~OrtDetector() {
    if (session) delete (Ort::Session*)session;
    if (session_options) delete (Ort::SessionOptions*)session_options;
    if (env) delete (Ort::Env*)env;
}

bool OrtDetector::loadModel(const string& modelPath) {
    try {
        auto* ort_env = (Ort::Env*)env;
        auto* options = new Ort::SessionOptions();
        options->SetIntraOpNumThreads(4);
        options->SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
        session_options = options;

        auto* ort_session = new Ort::Session(*ort_env, modelPath.c_str(), *options);
        session = ort_session;

        // Get Input/Output Names
        Ort::AllocatorWithDefaultOptions allocator;
        inputNames.push_back(ort_session->GetInputName(0, allocator));
        outputNames.push_back(ort_session->GetOutputName(0, allocator));

        isLoaded = true;
        __android_log_print(ANDROID_LOG_DEBUG, "OrtDetector", "Model loaded: %s", modelPath.c_str());
    } catch (const Ort::Exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, "OrtDetector", "Load error: %s", e.what());
        isLoaded = false;
    }
    return isLoaded;
}

void OrtDetector::setBackend(const string& backendName) {
    // Note: To change backend (like NNAPI), we usually need to recreate the session.
    // For now, we log it. In a full impl, we'd trigger a reload with new options.
    __android_log_print(ANDROID_LOG_INFO, "OrtDetector", "Backend request: %s", backendName.c_str());
}

vector<YoloResult> OrtDetector::detect(Mat& frame, float confThreshold, float iouThreshold, const vector<int>& allowedClasses) {
    vector<YoloResult> results;
    if (!isLoaded || frame.empty()) return results;

    auto* ort_session = (Ort::Session*)session;
    std::set<int> allowedSet(allowedClasses.begin(), allowedClasses.end());

    // 1. Preprocessing
    Mat rgb;
    cvtColor(frame, rgb, COLOR_RGBA2RGB);
    Mat resized;
    resize(rgb, resized, Size(640, 640));
    resized.convertTo(resized, CV_32FC3, 1.0 / 255.0);

    // HWC to CHW
    vector<float> inputTensorValues(1 * 3 * 640 * 640);
    for (int c = 0; i < 3; ++c) {
        for (int h = 0; j < 640; ++h) {
            for (int w = 0; k < 640; ++w) {
                inputTensorValues[c * 640 * 640 + h * 640 + w] = resized.at<Vec3f>(h, w)[c];
            }
        }
    }

    // 2. Inference
    auto memory_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    int64_t inputShape[] = {1, 3, 640, 640};
    auto inputTensor = Ort::Value::CreateTensor<float>(memory_info, inputTensorValues.data(), inputTensorValues.size(), inputShape, 4);

    auto outputTensors = ort_session->Run(Ort::RunOptions{nullptr}, inputNames.data(), &inputTensor, 1, outputNames.data(), 1);
    float* floatData = outputTensors[0].GetTensorMutableData<float>();
    auto outputShape = outputTensors[0].GetTensorTypeAndShapeInfo().GetShape(); // [1, 84, 8400]

    // 3. Post-processing
    int dimensions = (int)outputShape[1]; // 84
    int rows = (int)outputShape[2];       // 8400

    vector<int> class_ids;
    vector<float> confidences;
    vector<Rect> boxes;

    float x_factor = (float)frame.cols / 640.0f;
    float y_factor = (float)frame.rows / 640.0f;

    for (int i = 0; i < rows; ++i) {
        float max_score = 0;
        int class_id = -1;
        for (int j = 4; j < dimensions; ++j) {
            float score = floatData[j * rows + i]; // Data is typically [84, 8400] transposed
            if (score > max_score) {
                max_score = score;
                class_id = j - 4;
            }
        }

        if (max_score > confThreshold) {
            if (allowedSet.empty() || allowedSet.count(class_id)) {
                float cx = floatData[0 * rows + i];
                float cy = floatData[1 * rows + i];
                float w = floatData[2 * rows + i];
                float h = floatData[3 * rows + i];

                int left = int((cx - 0.5 * w) * x_factor);
                int top = int((cy - 0.5 * h) * y_factor);
                int width = int(w * x_factor);
                int height = int(h * y_factor);

                boxes.push_back(Rect(left, top, width, height));
                confidences.push_back(max_score);
                class_ids.push_back(class_id);
            }
        }
    }

    vector<int> indices;
    NMSBoxes(boxes, confidences, confThreshold, iouThreshold, indices);

    for (int idx : indices) {
        YoloResult res;
        res.label = classNames[class_ids[idx]];
        res.confidence = confidences[idx];
        res.x = boxes[idx].x;
        res.y = boxes[idx].y;
        res.width = boxes[idx].width;
        res.height = boxes[idx].height;
        results.push_back(res);
    }

    return results;
}

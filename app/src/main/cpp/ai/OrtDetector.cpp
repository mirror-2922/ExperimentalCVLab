#include "YoloDetector.h"
#include <onnxruntime_cxx_api.h>
#include <onnxruntime_float16.h>
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
    static Ort::Env ort_env(ORT_LOGGING_LEVEL_WARNING, "ECVL_Detector");
    env = &ort_env;
}

OrtDetector::~OrtDetector() {
    auto* ort_session = (Ort::Session*)session;
    if (ort_session) delete ort_session;
    auto* opts = (Ort::SessionOptions*)session_options;
    if (opts) delete opts;
}

bool OrtDetector::loadModel(const string& modelPath) {
    try {
        auto* ort_env = (Ort::Env*)env;
        auto* options = new Ort::SessionOptions();
        options->SetIntraOpNumThreads(4);
        options->SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
        
        // Handle potential backend-specific options here if needed
        session_options = options;

        auto* ort_session = new Ort::Session(*ort_env, modelPath.c_str(), *options);
        session = ort_session;

        Ort::AllocatorWithDefaultOptions allocator;
        inputNames.clear();
        outputNames.clear();
        
        auto in_name = ort_session->GetInputNameAllocated(0, allocator);
        inputNames.push_back(strdup(in_name.get()));
        
        auto out_name = ort_session->GetOutputNameAllocated(0, allocator);
        outputNames.push_back(strdup(out_name.get()));

        isLoaded = true;
        __android_log_print(ANDROID_LOG_DEBUG, "OrtDetector", "ECVL Model Ready: %s", modelPath.c_str());
    } catch (const Ort::Exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, "OrtDetector", "Load error: %s", e.what());
        isLoaded = false;
    }
    return isLoaded;
}

void OrtDetector::setBackend(const string& backendName) {
    __android_log_print(ANDROID_LOG_INFO, "OrtDetector", "Backend changed to: %s", backendName.size() > 0 ? backendName.c_str() : "CPU");
}

vector<YoloResult> OrtDetector::detect(Mat& frame, float confThreshold, float iouThreshold, const vector<int>& allowedClasses) {
    vector<YoloResult> results;
    if (!isLoaded || frame.empty()) return results;

    auto* ort_session = (Ort::Session*)session;
    std::set<int> allowedSet(allowedClasses.begin(), allowedClasses.end());

    // 1. Preprocessing
    Mat rgb;
    if (frame.channels() == 4) cvtColor(frame, rgb, COLOR_RGBA2RGB);
    else rgb = frame;

    Mat resized;
    resize(rgb, resized, Size(640, 640));
    resized.convertTo(resized, CV_32FC3, 1.0 / 255.0);

    // 2. Dynamic Input Type Handling (Fix for FP16 GPU crash)
    auto input_type_info = ort_session->GetInputTypeInfo(0);
    auto input_tensor_info = input_type_info.GetTensorTypeAndShapeInfo();
    ONNXTensorElementDataType expected_type = input_tensor_info.GetElementType();

    auto memory_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    int64_t inputShape[] = {1, 3, 640, 640};
    
    Ort::Value inputTensor(nullptr);

    if (expected_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16) {
        // Convert FP32 to FP16 for specialized backends (GPU/NPU)
        vector<uint16_t> fp16_values(1 * 3 * 640 * 640);
        for (int c = 0; c < 3; ++c) {
            for (int h = 0; h < 640; ++h) {
                for (int w = 0; w < 640; ++w) {
                    float val = resized.at<Vec3f>(h, w)[c];
                    // Simple FP32 to FP16 bit conversion helper from ORT
                    fp16_values[c * 640 * 640 + h * 640 + w] = Ort::Float16_t(val).val;
                }
            }
        }
        inputTensor = Ort::Value::CreateTensor<Ort::Float16_t>(memory_info, 
            reinterpret_cast<Ort::Float16_t*>(fp16_values.data()), fp16_values.size(), inputShape, 4);
        
        auto outputTensors = ort_session->Run(Ort::RunOptions{nullptr}, inputNames.data(), &inputTensor, 1, outputNames.data(), 1);
        processResults(outputTensors[0], frame, confThreshold, iouThreshold, allowedSet, results);
    } else {
        // Standard FP32 path
        vector<float> inputTensorValues(1 * 3 * 640 * 640);
        for (int c = 0; c < 3; ++c) {
            for (int h = 0; h < 640; ++h) {
                for (int w = 0; w < 640; ++w) {
                    inputTensorValues[c * 640 * 640 + h * 640 + w] = resized.at<Vec3f>(h, w)[c];
                }
            }
        }
        inputTensor = Ort::Value::CreateTensor<float>(memory_info, inputTensorValues.data(), inputTensorValues.size(), inputShape, 4);
        
        auto outputTensors = ort_session->Run(Ort::RunOptions{nullptr}, inputNames.data(), &inputTensor, 1, outputNames.data(), 1);
        processResults(outputTensors[0], frame, confThreshold, iouThreshold, allowedSet, results);
    }

    return results;
}

void OrtDetector::processResults(Ort::Value& outputTensor, Mat& frame, float confThreshold, float iouThreshold, const std::set<int>& allowedSet, vector<YoloResult>& results) {
    float* floatData = outputTensor.GetTensorMutableData<float>();
    auto outputShape = outputTensor.GetTensorTypeAndShapeInfo().GetShape();

    int dimensions = (int)outputShape[1]; 
    int rows = (int)outputShape[2];       

    vector<int> class_ids;
    vector<float> confidences;
    vector<Rect> boxes;

    float x_factor = (float)frame.cols / 640.0f;
    float y_factor = (float)frame.rows / 640.0f;

    for (int i = 0; i < rows; ++i) {
        float max_score = 0;
        int class_id = -1;
        for (int j = 4; j < dimensions; ++j) {
            float score = floatData[j * rows + i];
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
    cv::dnn::NMSBoxes(boxes, confidences, confThreshold, iouThreshold, indices);

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
}
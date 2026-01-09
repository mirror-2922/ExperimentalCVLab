#include "YoloDetector.h"
#include <android/log.h>
#include <set>

using namespace cv;
using namespace std;
using namespace cv::dnn;

OpenCVDetector::OpenCVDetector() : isLoaded(false) {
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
}

bool OpenCVDetector::loadModel(const string& modelPath) {
    try {
        net = readNet(modelPath);
        net.setPreferableBackend(DNN_BACKEND_OPENCV);
        net.setPreferableTarget(DNN_TARGET_CPU);
        isLoaded = true;
        __android_log_print(ANDROID_LOG_DEBUG, "OpenCVDetector", "Model loaded from %s", modelPath.c_str());
    } catch (const cv::Exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, "OpenCVDetector", "Load error: %s", e.what());
        isLoaded = false;
    }
    return isLoaded;
}

void OpenCVDetector::setBackend(const string& backendName) {
    if (!isLoaded) return;
    
    if (backendName == "GPU (OpenCL)") {
        net.setPreferableBackend(DNN_BACKEND_OPENCV);
        net.setPreferableTarget(DNN_TARGET_OPENCL);
    } else if (backendName == "NPU (NNAPI)") {
        net.setPreferableBackend(DNN_BACKEND_DEFAULT);
        net.setPreferableTarget(DNN_TARGET_NPU);
    } else {
        net.setPreferableBackend(DNN_BACKEND_OPENCV);
        net.setPreferableTarget(DNN_TARGET_CPU);
    }
}

vector<YoloResult> OpenCVDetector::detect(Mat& frame, float confThreshold, float iouThreshold, const vector<int>& allowedClasses) {
    vector<YoloResult> results;
    if (!isLoaded || frame.empty()) return results;

    std::set<int> allowedSet(allowedClasses.begin(), allowedClasses.end());

    int imgW = frame.cols;
    int imgH = frame.rows;

    // 1. Preprocessing
    Mat rgb;
    if (frame.channels() == 4) cvtColor(frame, rgb, COLOR_RGBA2RGB);
    else cvtColor(frame, rgb, COLOR_BGR2RGB);

    Mat blob;
    blobFromImage(rgb, blob, 1.0/255.0, Size(netInputWidth, netInputHeight), Scalar(), false, false);
    net.setInput(blob);

    // 2. Inference
    vector<Mat> outputs;
    net.forward(outputs, net.getUnconnectedOutLayersNames());

    // 3. Post-processing
    Mat output = outputs[0];
    int dimensions = output.size[1];
    int rows = output.size[2];

    Mat out2D = output.reshape(1, dimensions);
    Mat t_output;
    transpose(out2D, t_output);
    
    float* data = (float*)t_output.data;
    float x_factor = (float)imgW / netInputWidth;
    float y_factor = (float)imgH / netInputHeight;

    vector<int> class_ids;
    vector<float> confidences;
    vector<Rect> boxes;

    for (int i = 0; i < rows; ++i) {
        float* row_ptr = data + (i * dimensions);
        float* scores_ptr = row_ptr + 4;
        
        Point classIdPoint;
        double max_class_score;
        minMaxLoc(Mat(1, classNames.size(), CV_32F, scores_ptr), 0, &max_class_score, 0, &classIdPoint);
        
        if (max_class_score > confThreshold) {
            int classId = classIdPoint.x;
            if (allowedSet.empty() || allowedSet.count(classId)) {
                float cx = row_ptr[0];
                float cy = row_ptr[1];
                float w = row_ptr[2];
                float h = row_ptr[3];

                int left = int((cx - 0.5 * w) * x_factor);
                int top = int((cy - 0.5 * h) * y_factor);
                int width = int(w * x_factor);
                int height = int(h * y_factor);

                boxes.push_back(Rect(left, top, width, height));
                confidences.push_back((float)max_class_score);
                class_ids.push_back(classId);
            }
        }
    }

    vector<int> indices;
    dnn::NMSBoxes(boxes, confidences, confThreshold, iouThreshold, indices);

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
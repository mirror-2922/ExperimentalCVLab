#include "engine.h"

void InferenceEngine::composite(cv::Mat& frame, const std::vector<DetectionResult>& results) {
    for (const auto& res : results) {
        // Convert normalized coordinates back to pixels
        int x = (int)(res.box.x * frame.cols);
        int y = (int)(res.box.y * frame.rows);
        int w = (int)(res.box.width * frame.cols);
        int h = (int)(res.box.height * frame.rows);

        cv::Rect rect(x, y, w, h);
        
        // Draw Bounding Box
        cv::rectangle(frame, rect, cv::Scalar(0, 255, 0, 255), 2);

        // Draw Label Background
        std::string labelStr = res.label + " " + std::to_string((int)(res.confidence * 100)) + "%";
        int baseLine;
        cv::Size labelSize = cv::getTextSize(labelStr, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);
        cv::rectangle(frame, cv::Rect(x, y - labelSize.height, labelSize.width, labelSize.height + baseLine), cv::Scalar(0, 255, 0, 255), -1);

        // Draw Text
        cv::putText(frame, labelStr, cv::Point(x, y), cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(255, 255, 255, 255), 1);
    }
}

#pragma once
#include <string>
#include <vector>

struct YoloResult {
    int class_index;
    std::string label;
    float confidence;
    float x;      // 改为 float
    float y;      // 改为 float
    float width;  // 改为 float
    float height; // 改为 float
};

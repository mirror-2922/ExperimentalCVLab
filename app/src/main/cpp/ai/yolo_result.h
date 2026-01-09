#pragma once
#include <string>
#include <vector>

struct YoloResult {
    std::string label;
    float confidence;
    int x;
    int y;
    int width;
    int height;
};

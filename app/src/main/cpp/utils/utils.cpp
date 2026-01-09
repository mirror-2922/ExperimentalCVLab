#include "utils.h"

cv::Mat& getMat(jlong addr) {
    return *(cv::Mat*)addr;
}

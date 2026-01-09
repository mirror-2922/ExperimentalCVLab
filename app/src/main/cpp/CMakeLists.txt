cmake_minimum_required(VERSION 3.22.1)

project("beautyapp")

set(OpenCV_DIR "E:/projects/AndroidStudioProjects/BeautyApp/opencv-sdk/OpenCV-android-sdk/sdk/native/jni")
find_package(OpenCV REQUIRED)

# ORT remains implement in code but disabled in build due to link constraints
# To enable ORT: 
# 1. Download ORT Android AAR and extract libs/headers to cpp/libs
# 2. Add find_library and target_link_libraries below

add_library(beautyapp SHARED 
    native-lib.cpp
    filters/filters.cpp
    ai/ai.cpp
    ai/YoloDetector.cpp
    # ai/OrtDetector.cpp
    utils/utils.cpp
)

target_link_libraries(beautyapp
        ${OpenCV_LIBS}
        log)

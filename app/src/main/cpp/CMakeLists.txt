cmake_minimum_required(VERSION 3.22.1)

project("beautyapp")

set(OpenCV_DIR "E:/projects/AndroidStudioProjects/BeautyApp/opencv-sdk/OpenCV-android-sdk/sdk/native/jni")

find_package(OpenCV REQUIRED)

add_library(beautyapp SHARED 
    native-lib.cpp
    filters/filters.cpp
    ai/ai.cpp
    ai/YoloDetector.cpp
    utils/utils.cpp
)

target_link_libraries(beautyapp
        ${OpenCV_LIBS}
        log)

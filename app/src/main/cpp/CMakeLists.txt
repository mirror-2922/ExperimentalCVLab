cmake_minimum_required(VERSION 3.22.1)

project("beautyapp")

# --- Official OpenCV Integration via Prefab ---
find_package(OpenCV REQUIRED CONFIG)

# --- Manual ONNX Runtime Integration (Extracted AAR) ---
include_directories(SYSTEM ${ORT_PATH}/headers)
add_library(ort_lib SHARED IMPORTED)
set_target_properties(ort_lib PROPERTIES IMPORTED_LOCATION
    ${ORT_PATH}/jni/${ANDROID_ABI}/libonnxruntime.so)

# Modular JNI Bridges
add_library(beautyapp SHARED 
    native-lib.cpp
    jni/jni_image_utils.cpp
    jni/jni_filters.cpp
    jni/jni_ai.cpp
    filters/filters.cpp
    ai/ai.cpp
    ai/YoloDetector.cpp
    ai/OrtDetector.cpp
    utils/utils.cpp
    utils/NativeCamera.cpp
)

target_link_libraries(beautyapp
        OpenCV::opencv_java4 # Official target name from Prefab
        ort_lib
        "-Wl,--no-fatal-warnings"
        log
        camera2ndk
        mediandk
        android)
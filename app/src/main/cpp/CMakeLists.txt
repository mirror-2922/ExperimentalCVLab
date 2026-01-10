cmake_minimum_required(VERSION 3.22.1)

project("beautyapp")

# --- Official OpenCV Integration via Prefab (Primary) ---
find_package(OpenCV REQUIRED CONFIG)

# --- Manual ONNX Runtime Integration ---
include_directories(SYSTEM ${ORT_PATH}/headers)
add_library(ort_lib SHARED IMPORTED)
set_target_properties(ort_lib PROPERTIES IMPORTED_LOCATION
    ${ORT_PATH}/jni/${ANDROID_ABI}/libonnxruntime.so)

# --- NCNN Support (Optional based on libs dir) ---
set(NCNN_BASE ${CMAKE_SOURCE_DIR}/libs/ncnn/${ANDROID_ABI})
if(EXISTS "${NCNN_BASE}/lib/cmake/ncnn/ncnnConfig.cmake")
    set(ncnn_DIR ${NCNN_BASE}/lib/cmake/ncnn)
    find_package(ncnn REQUIRED)
    add_definitions(-DUSE_NCNN)
    message(STATUS "NCNN backend ENABLED")
else()
    message(STATUS "NCNN backend DISABLED (libs/ncnn not found)")
endif()

# Source Files
set(SRC_LIST
    native-lib.cpp
    jni/jni_image_utils.cpp
    jni/jni_filters.cpp
    jni/jni_ai.cpp
    filters/filters.cpp
    ai/ai.cpp
    ai/engine/engine.cpp
    ai/engine/OrtEngine.cpp
    ai/engine/DNNEngine.cpp
    utils/utils.cpp
    utils/NativeCamera.cpp
    utils/converter/pnnx_converter.cpp
)

if(USE_NCNN)
    list(APPEND SRC_LIST ai/engine/NCNNEngine.cpp)
endif()

add_library(beautyapp SHARED ${SRC_LIST})

target_link_libraries(beautyapp
        OpenCV::opencv_java4 
        ort_lib
        "-Wl,--no-fatal-warnings"
        log
        camera2ndk
        mediandk
        android)

if(USE_NCNN)
    target_link_libraries(beautyapp ncnn)
endif()

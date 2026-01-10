#pragma once

#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraCaptureSession.h>
#include <media/NdkImageReader.h>
#include <android/native_window_jni.h>
#include <opencv2/opencv.hpp>
#include <string>
#include <vector>
#include <mutex>
#include <chrono>
#include <atomic>
#include "../ai/engine/engine.h"

class NativeCamera {
public:
    NativeCamera();
    ~NativeCamera();

    bool open(int facing, int width, int height, jobject viewfinderSurface, jobject mlKitSurface);
    void close();

    bool fetch_latest_frame(int* pixels, int w, int h);

private:
    ACameraManager* camera_manager = nullptr;
    ACameraDevice* camera_device = nullptr;
    ACaptureRequest* capture_request = nullptr;
    ACameraCaptureSession* capture_session = nullptr;
    ACaptureSessionOutputContainer* output_container = nullptr;
    
    AImageReader* image_reader = nullptr;
    ANativeWindow* image_reader_surface = nullptr;
    ACameraOutputTarget* image_reader_target = nullptr;
    ACaptureSessionOutput* image_reader_session_output = nullptr;

    // 渲染输出
    ANativeWindow* viewfinder_window = nullptr;
    ANativeWindow* mlkit_window = nullptr;
    std::mutex window_mutex;

    // 内部处理帧
    cv::Mat latest_frame;
    std::mutex frame_mutex;

    // Callbacks
    ACameraDevice_StateCallbacks device_callbacks;
    ACameraCaptureSession_stateCallbacks session_callbacks;
    AImageReader_ImageListener reader_listener;

    static void onDeviceDisconnected(void* context, ACameraDevice* device);
    static void onDeviceError(void* context, ACameraDevice* device, int error);
    static void onSessionClosed(void* context, ACameraCaptureSession* session);
    static void onSessionReady(void* context, ACameraCaptureSession* session);
    static void onSessionActive(void* context, ACameraCaptureSession* session);
    static void onImageAvailable(void* context, AImageReader* reader);

    int camera_facing = 0;
    int sensor_orientation = 0;
    std::atomic<bool> is_running{false};

    // Performance Metrics
    std::chrono::steady_clock::time_point last_fps_time;
    int frame_count = 0;
    float current_fps = 0;
};
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

class NativeCamera {
public:
    NativeCamera();
    virtual ~NativeCamera();

    bool open(int facing, int width, int height, jobject viewfinderSurface, jobject mlKitSurface);
    void close();

    // 状态回调由具体的逻辑消费
    virtual void on_image(const cv::Mat& rgba);

private:
    ACameraManager* camera_manager = nullptr;
    ACameraDevice* camera_device = nullptr;
    ACaptureRequest* capture_request = nullptr;
    ACameraCaptureSession* capture_session = nullptr;
    ACaptureSessionOutputContainer* output_container = nullptr;
    
    // 唯一的硬件输出目标
    AImageReader* image_reader = nullptr;
    ANativeWindow* image_reader_surface = nullptr;
    ACameraOutputTarget* image_reader_target = nullptr;
    ACaptureSessionOutput* image_reader_session_output = nullptr;

    // UI 和 ML Kit 的渲染窗口
    ANativeWindow* viewfinder_window = nullptr;
    ANativeWindow* mlkit_window = nullptr;

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
    int current_width = 0;
    int current_height = 0;

    std::mutex window_mutex;
    std::atomic<bool> is_running{false};

    // Perf
    std::chrono::steady_clock::time_point last_fps_time;
    int frame_count = 0;
    float current_fps = 0;
};
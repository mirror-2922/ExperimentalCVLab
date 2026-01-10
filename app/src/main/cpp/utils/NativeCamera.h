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

class NativeCamera {
public:
    NativeCamera();
    ~NativeCamera();

    bool start(int facing, int width, int height, jobject viewfinderSurface);
    void stop();

private:
    ACameraManager* cameraManager = nullptr;
    ACameraDevice* cameraDevice = nullptr;
    
    ANativeWindow* viewfinderWindow = nullptr;
    AImageReader* aiReader = nullptr; // 640x640 专用

    ACaptureRequest* captureRequest = nullptr;
    ACameraCaptureSession* captureSession = nullptr;
    ACaptureSessionOutputContainer* outputContainer = nullptr;
    
    ACameraOutputTarget* viewfinderTarget = nullptr;
    ACameraOutputTarget* aiTarget = nullptr;

    ACaptureSessionOutput* sessionOutputViewfinder = nullptr;
    ACaptureSessionOutput* sessionOutputAi = nullptr;

    static void onImageAvailable(void* context, AImageReader* reader);
    void processAiFrame(AImageReader* reader);

    ACameraDevice_StateCallbacks deviceCallbacks;
    ACameraCaptureSession_stateCallbacks sessionCallbacks;

    int currentWidth = 0;
    int currentHeight = 0;
    int sensorOrientation = 0;
    int lensFacing = 0;
};

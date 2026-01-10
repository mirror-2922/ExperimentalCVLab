#include "NativeCamera.h"
#include <android/log.h>
#include <media/NdkImage.h>
#include "../ai/ai.h"
#include "../filters/filters.h"
#include "../utils/utils.h"
#include <algorithm>

#define TAG "NativeCamera"

// Helper function to update detection results in ai.cpp
extern void updateDetectionsBinary(const std::vector<YoloResult>& results);
extern float getNativeConf();
extern float getNativeIoU();
extern std::vector<int> getNativeClasses();
extern std::unique_ptr<InferenceEngine> detector;

NativeCamera::NativeCamera() {
    cameraManager = ACameraManager_create();
    deviceCallbacks = {this, nullptr, nullptr};
    sessionCallbacks = {this, nullptr, nullptr, nullptr};
}

NativeCamera::~NativeCamera() {
    stop();
    if (cameraManager) ACameraManager_delete(cameraManager);
}

bool NativeCamera::start(int facing, int width, int height, jobject vSurface) {
    stop();
    lensFacing = facing;
    currentWidth = width;
    currentHeight = height;

    ACameraIdList* cameraIdList = nullptr;
    ACameraManager_getCameraIdList(cameraManager, &cameraIdList);
    const char* selectedId = cameraIdList->cameraIds[0];
    
    for (int i = 0; i < cameraIdList->numCameras; ++i) {
        ACameraMetadata* chars = nullptr;
        ACameraManager_getCameraCharacteristics(cameraManager, cameraIdList->cameraIds[i], &chars);
        ACameraMetadata_const_entry entry;
        ACameraMetadata_getConstEntry(chars, ACAMERA_LENS_FACING, &entry);
        if (entry.data.u8[0] == facing) {
            selectedId = cameraIdList->cameraIds[i];
            ACameraMetadata_getConstEntry(chars, ACAMERA_SENSOR_ORIENTATION, &entry);
            sensorOrientation = entry.data.i32[0];
            ACameraMetadata_free(chars);
            break;
        }
        ACameraMetadata_free(chars);
    }

    ACameraManager_openCamera(cameraManager, selectedId, &deviceCallbacks, &cameraDevice);
    ACameraManager_deleteCameraIdList(cameraIdList);

    // AI Pipeline: 640x640 Fixed for YOLO
    AImageReader_new(640, 640, AIMAGE_FORMAT_YUV_420_888, 2, &aiReader);
    AImageReader_ImageListener listener = {this, onImageAvailable};
    AImageReader_setImageListener(aiReader, &listener);

    viewfinderWindow = ANativeWindow_fromSurface(getJNIEnv(), vSurface);

    ACaptureSessionOutputContainer_create(&outputContainer);
    ACaptureSessionOutput_create(viewfinderWindow, &sessionOutputViewfinder);
    ANativeWindow* aiWindow = nullptr;
    AImageReader_getWindow(aiReader, &aiWindow);
    ACaptureSessionOutput_create(aiWindow, &sessionOutputAi);

    ACaptureSessionOutputContainer_add(outputContainer, sessionOutputViewfinder);
    ACaptureSessionOutputContainer_add(outputContainer, sessionOutputAi);

    ACameraDevice_createCaptureSession(cameraDevice, outputContainer, &sessionCallbacks, &captureSession);

    ACameraDevice_createCaptureRequest(cameraDevice, TEMPLATE_PREVIEW, &captureRequest);
    ACameraOutputTarget_create(viewfinderWindow, &viewfinderTarget);
    ACameraOutputTarget_create(aiWindow, &aiTarget);

    ACaptureRequest_addTarget(captureRequest, viewfinderTarget);
    ACaptureRequest_addTarget(captureRequest, aiTarget);

    ACameraCaptureSession_setRepeatingRequest(captureSession, nullptr, 1, &captureRequest, nullptr);

    return true;
}

void NativeCamera::stop() {
    if (captureSession) { ACameraCaptureSession_stopRepeating(captureSession); ACameraCaptureSession_close(captureSession); captureSession = nullptr; }
    if (cameraDevice) { ACameraDevice_close(cameraDevice); cameraDevice = nullptr; }
    if (aiReader) { AImageReader_delete(aiReader); aiReader = nullptr; }
    if (viewfinderWindow) { ANativeWindow_release(viewfinderWindow); viewfinderWindow = nullptr; }
    if (captureRequest) { ACaptureRequest_free(captureRequest); captureRequest = nullptr; }
    if (outputContainer) { ACaptureSessionOutputContainer_free(outputContainer); outputContainer = nullptr; }
}

void NativeCamera::onImageAvailable(void* context, AImageReader* reader) {
    static_cast<NativeCamera*>(context)->processAiFrame(reader);
}

void NativeCamera::processAiFrame(AImageReader* reader) {
    AImage* image = nullptr;
    if (AImageReader_acquireLatestImage(reader, &image) != AMEDIA_OK) return;

    int32_t w, h;
    AImage_getWidth(image, &w);
    AImage_getHeight(image, &h);

    uint8_t *y, *u, *v;
    int yL, uL, vL, yS, uS, vS, pS;
    AImage_getPlaneData(image, 0, &y, &yL); AImage_getPlaneRowStride(image, 0, &yS);
    AImage_getPlaneData(image, 1, &u, &uL); AImage_getPlaneRowStride(image, 1, &uS); AImage_getPlanePixelStride(image, 1, &pS);
    AImage_getPlaneData(image, 2, &v, &vL); AImage_getPlaneRowStride(image, 2, &vS);

    // AI Frame is already 640x640 from hardware, just need conversion
    cv::Mat yuv(h + h/2, w, CV_8UC1);
    for(int i=0; i<h; i++) memcpy(yuv.ptr(i), y + i*yS, w);
    uint8_t* uv = yuv.ptr(h);
    for(int i=0; i<h/2; i++) {
        for(int j=0; j<w/2; j++) {
            uv[i*w + j*2] = v[i*vS + j*pS];
            uv[i*w + j*2 + 1] = u[i*uS + j*pS];
        }
    }
    cv::Mat rgba;
    cv::cvtColor(yuv, rgba, cv::COLOR_YUV2RGBA_NV21);
    AImage_delete(image);

    // Preprocessing (Rotate only, aspect is already 1:1)
    cv::Mat processed;
    switch (sensorOrientation) {
        case 90: cv::rotate(rgba, processed, cv::ROTATE_90_CLOCKWISE); break;
        case 180: cv::rotate(rgba, processed, cv::ROTATE_180); break;
        case 270: cv::rotate(rgba, processed, cv::ROTATE_90_COUNTERCLOCKWISE); break;
        default: processed = rgba; break;
    }
    if (lensFacing == 0) cv::flip(processed, processed, 1);

    // Inference
    if (getNativeMode() == 1 && detector) {
        auto results = detector->detect(processed, getNativeConf(), getNativeIoU(), getNativeClasses());
        // Normalize results to [0, 1] relative to the processed square frame
        for (auto& res : results) {
            res.x /= processed.cols;
            res.y /= processed.rows;
            res.width /= processed.cols;
            res.height /= processed.rows;
        }
        updateDetectionsBinary(results);
    }
}

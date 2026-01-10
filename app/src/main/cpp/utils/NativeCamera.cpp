#include "NativeCamera.h"
#include <android/log.h>
#include <media/NdkImage.h>
#include "../ai/ai.h"
#include "../filters/filters.h"
#include "../utils/utils.h"
#include <algorithm>

#define TAG "NativeCamera"

extern void updateDetectionsBinary(const std::vector<YoloResult>& results);
extern float getNativeConf();
extern float getNativeIoU();
extern std::vector<int> getNativeClasses();
extern std::string getNativeFilter();
extern int getNativeMode();
extern std::unique_ptr<InferenceEngine> detector;
extern void updatePerfMetrics(float fps, float inferenceTime, int w, int h);

cv::Mat aImgToMat(AImage* image) {
    int32_t w, h;
    AImage_getWidth(image, &w);
    AImage_getHeight(image, &h);
    uint8_t *y, *u, *v;
    int yL, uL, vL, yS, uS, vS, pS;
    AImage_getPlaneData(image, 0, &y, &yL); AImage_getPlaneRowStride(image, 0, &yS);
    AImage_getPlaneData(image, 1, &u, &uL); AImage_getPlaneRowStride(image, 1, &uS); AImage_getPlanePixelStride(image, 1, &pS);
    AImage_getPlaneData(image, 2, &v, &vL); AImage_getPlaneRowStride(image, 2, &vS);

    cv::Mat yuv(h + h/2, w, CV_8UC1);
    for(int i=0; i<h; i++) memcpy(yuv.ptr(i), y + i*yS, w);
    uint8_t* uvPtr = yuv.ptr(h);
    for(int i=0; i<h/2; i++) {
        for(int j=0; j<w/2; j++) {
            uvPtr[i*w + j*2] = v[i*vS + j*pS];
            uvPtr[i*w + j*2 + 1] = u[i*uS + j*pS];
        }
    }
    cv::Mat rgba;
    cv::cvtColor(yuv, rgba, cv::COLOR_YUV2RGBA_NV21);
    return rgba;
}

NativeCamera::NativeCamera() {
    cameraManager = ACameraManager_create();
    cameraDevice = nullptr;
    viewfinderWindow = nullptr;
    mlKitWindow = nullptr;
    viewfinderReader = nullptr;
    aiReader = nullptr;
    captureRequest = nullptr;
    captureSession = nullptr;
    outputContainer = nullptr;
    viewfinderTarget = nullptr;
    mlKitTarget = nullptr;
    aiTarget = nullptr;
    sessionOutputViewfinder = nullptr;
    sessionOutputMlKit = nullptr;
    sessionOutputAi = nullptr;
    
    deviceCallbacks = {this, nullptr, nullptr};
    sessionCallbacks = {this, nullptr, nullptr, nullptr};
    lastFpsUpdateTime = std::chrono::steady_clock::now();
    frameCount = 0;
    currentFps = 0;
}

NativeCamera::~NativeCamera() {
    stop();
    if (cameraManager) ACameraManager_delete(cameraManager);
}

bool NativeCamera::start(int facing, int width, int height, jobject vSurface, jobject mSurface) {
    stop();
    lensFacing = facing;
    currentWidth = width;
    currentHeight = height;

    ACameraIdList* cameraIdList = nullptr;
    ACameraManager_getCameraIdList(cameraManager, &cameraIdList);
    const char* selectedId = nullptr;
    
    if (cameraIdList != nullptr && cameraIdList->numCameras > 0) {
        selectedId = cameraIdList->cameraIds[0];
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
    }

    if (selectedId == nullptr) {
        if (cameraIdList != nullptr) ACameraManager_deleteCameraIdList(cameraIdList);
        return false;
    }

    ACameraManager_openCamera(cameraManager, selectedId, &deviceCallbacks, &cameraDevice);
    ACameraManager_deleteCameraIdList(cameraIdList);

    // 1. Setup Readers
    AImageReader_new(width, height, AIMAGE_FORMAT_YUV_420_888, 2, &viewfinderReader);
    AImageReader_ImageListener vListener = {this, onViewfinderImage};
    AImageReader_setImageListener(viewfinderReader, &vListener);

    AImageReader_new(640, 640, AIMAGE_FORMAT_YUV_420_888, 2, &aiReader);
    AImageReader_ImageListener aiListener = {this, onAiImage};
    AImageReader_setImageListener(aiReader, &aiListener);

    // 2. Wrap Surfaces
    viewfinderWindow = ANativeWindow_fromSurface(getJNIEnv(), vSurface);
    mlKitWindow = ANativeWindow_fromSurface(getJNIEnv(), mSurface);
    
    // Crucial: Set format to match our OpenCV output
    ANativeWindow_setBuffersGeometry(viewfinderWindow, 0, 0, WINDOW_FORMAT_RGBA_8888);

    // 3. Configure Session
    ACaptureSessionOutputContainer_create(&outputContainer);
    
    ANativeWindow *vReaderWin = nullptr, *aiWin = nullptr;
    AImageReader_getWindow(viewfinderReader, &vReaderWin);
    AImageReader_getWindow(aiReader, &aiWin);

    ACaptureSessionOutput_create(vReaderWin, &sessionOutputViewfinder);
    ACaptureSessionOutput_create(mlKitWindow, &sessionOutputMlKit);
    ACaptureSessionOutput_create(aiWin, &sessionOutputAi);

    ACaptureSessionOutputContainer_add(outputContainer, sessionOutputViewfinder);
    ACaptureSessionOutputContainer_add(outputContainer, sessionOutputMlKit);
    ACaptureSessionOutputContainer_add(outputContainer, sessionOutputAi);

    ACameraDevice_createCaptureSession(cameraDevice, outputContainer, &sessionCallbacks, &captureSession);

    // 4. Start Request
    ACameraDevice_createCaptureRequest(cameraDevice, TEMPLATE_PREVIEW, &captureRequest);
    
    ACameraOutputTarget_create(vReaderWin, &viewfinderTarget);
    ACameraOutputTarget_create(mlKitWindow, &mlKitTarget);
    ACameraOutputTarget_create(aiWin, &aiTarget);

    ACaptureRequest_addTarget(captureRequest, viewfinderTarget);
    ACaptureRequest_addTarget(captureRequest, mlKitTarget);
    ACaptureRequest_addTarget(captureRequest, aiTarget);

    ACameraCaptureSession_setRepeatingRequest(captureSession, nullptr, 1, &captureRequest, nullptr);

    return true;
}

void NativeCamera::stop() {
    if (captureSession) { ACameraCaptureSession_stopRepeating(captureSession); ACameraCaptureSession_close(captureSession); captureSession = nullptr; }
    if (cameraDevice) { ACameraDevice_close(cameraDevice); cameraDevice = nullptr; }
    if (viewfinderReader) { AImageReader_delete(viewfinderReader); viewfinderReader = nullptr; }
    if (aiReader) { AImageReader_delete(aiReader); aiReader = nullptr; }
    if (viewfinderWindow) { ANativeWindow_release(viewfinderWindow); viewfinderWindow = nullptr; }
    if (mlKitWindow) { ANativeWindow_release(mlKitWindow); mlKitWindow = nullptr; }
    if (captureRequest) { ACaptureRequest_free(captureRequest); captureRequest = nullptr; }
    if (outputContainer) { ACaptureSessionOutputContainer_free(outputContainer); outputContainer = nullptr; }
    if (viewfinderTarget) { ACameraOutputTarget_free(viewfinderTarget); viewfinderTarget = nullptr; }
    if (mlKitTarget) { ACameraOutputTarget_free(mlKitTarget); mlKitTarget = nullptr; }
    if (aiTarget) { ACameraOutputTarget_free(aiTarget); aiTarget = nullptr; }
}

void NativeCamera::onViewfinderImage(void* context, AImageReader* reader) {
    static_cast<NativeCamera*>(context)->processViewfinderFrame(reader);
}

void NativeCamera::onAiImage(void* context, AImageReader* reader) {
    static_cast<NativeCamera*>(context)->processAiFrame(reader);
}

void NativeCamera::processViewfinderFrame(AImageReader* reader) {
    AImage* image = nullptr;
    if (AImageReader_acquireLatestImage(reader, &image) != AMEDIA_OK) return;

    cv::Mat rgba = aImgToMat(image);
    AImage_delete(image);

    // Rotate & Flip
    cv::Mat rotated;
    switch (sensorOrientation) {
        case 90: cv::rotate(rgba, rotated, cv::ROTATE_90_CLOCKWISE); break;
        case 180: cv::rotate(rgba, rotated, cv::ROTATE_180); break;
        case 270: cv::rotate(rgba, rotated, cv::ROTATE_90_COUNTERCLOCKWISE); break;
        default: rotated = rgba; break;
    }
    if (lensFacing == 0) cv::flip(rotated, rotated, 1);

    // 1:1 Square Crop to match AI and Overlay
    int side = std::min(rotated.cols, rotated.rows);
    cv::Rect roi((rotated.cols - side) / 2, (rotated.rows - side) / 2, side, side);
    cv::Mat cropped = rotated(roi).clone();

    // Apply Filter
    std::string filter = getNativeFilter();
    if (filter != "Normal") {
        if (filter == "Beauty") applyBeauty(cropped);
        else if (filter == "Dehaze") applyDehaze(cropped);
        else if (filter == "Underwater") applyUnderwater(cropped);
        else if (filter == "Stage") applyStage(cropped);
        else if (filter == "Gray") applyGray(cropped);
        else if (filter == "Histogram") applyHistEq(cropped);
        else if (filter == "Binary") applyBinary(cropped);
        else if (filter == "Morph Open") applyMorphOpen(cropped);
        else if (filter == "Morph Close") applyMorphClose(cropped);
        else if (filter == "Blur") applyBlur(cropped);
    }

    // Render to Surface
    if (viewfinderWindow) {
        ANativeWindow_Buffer buffer;
        if (ANativeWindow_lock(viewfinderWindow, &buffer, nullptr) == 0) {
            if (buffer.width > 0 && buffer.height > 0 && buffer.bits != nullptr) {
                cv::Mat dst(buffer.height, buffer.width, CV_8UC4, buffer.bits, buffer.stride * 4);
                cv::resize(cropped, dst, dst.size());
            }
            ANativeWindow_unlockAndPost(viewfinderWindow);
        }
    }

    frameCount++;
    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - lastFpsUpdateTime).count();
    if (elapsed > 1000) {
        currentFps = frameCount * 1000.0f / elapsed;
        frameCount = 0;
        lastFpsUpdateTime = now;
    }
    updatePerfMetrics(currentFps, 0, currentWidth, currentHeight);
}

void NativeCamera::processAiFrame(AImageReader* reader) {
    AImage* image = nullptr;
    if (AImageReader_acquireLatestImage(reader, &image) != AMEDIA_OK) return;

    if (getNativeMode() == 1 && detector) {
        auto start = std::chrono::steady_clock::now();
        cv::Mat rgba = aImgToMat(image);
        cv::Mat processed;
        switch (sensorOrientation) {
            case 90: cv::rotate(rgba, processed, cv::ROTATE_90_CLOCKWISE); break;
            case 180: cv::rotate(rgba, processed, cv::ROTATE_180); break;
            case 270: cv::rotate(rgba, processed, cv::ROTATE_90_COUNTERCLOCKWISE); break;
            default: processed = rgba; break;
        }
        if (lensFacing == 0) cv::flip(processed, processed, 1);

        auto results = detector->detect(processed, getNativeConf(), getNativeIoU(), getNativeClasses());
        for (auto& res : results) {
            res.x /= (float)processed.cols; res.y /= (float)processed.rows;
            res.width /= (float)processed.cols; res.height /= (float)processed.rows;
        }
        updateDetectionsBinary(results);
        
        auto end = std::chrono::steady_clock::now();
        float latency = (float)std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
        updatePerfMetrics(currentFps, latency, 640, 640);
    }
    AImage_delete(image);
}

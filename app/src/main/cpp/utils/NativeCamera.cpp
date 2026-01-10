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
    if (AImage_getWidth(image, &w) != AMEDIA_OK || AImage_getHeight(image, &h) != AMEDIA_OK) return cv::Mat();
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
    camera_manager = ACameraManager_create();
    device_callbacks = {this, onDeviceDisconnected, onDeviceError};
    session_callbacks = {this, onSessionClosed, onSessionReady, onSessionActive};
    reader_listener = {this, onImageAvailable};
}

NativeCamera::~NativeCamera() {
    close();
    if (camera_manager) ACameraManager_delete(camera_manager);
}

bool NativeCamera::open(int facing, int width, int height, jobject vSurface, jobject mSurface) {
    close();
    camera_facing = facing;
    current_width = width;
    current_height = height;

    ACameraIdList* id_list = nullptr;
    ACameraManager_getCameraIdList(camera_manager, &id_list);
    const char* selected_id = id_list->cameraIds[0];
    for (int i = 0; i < id_list->numCameras; i++) {
        ACameraMetadata* chars = nullptr;
        ACameraManager_getCameraCharacteristics(camera_manager, id_list->cameraIds[i], &chars);
        ACameraMetadata_const_entry entry;
        ACameraMetadata_getConstEntry(chars, ACAMERA_LENS_FACING, &entry);
        if (entry.data.u8[0] == (uint8_t)facing) {
            selected_id = id_list->cameraIds[i];
            ACameraMetadata_getConstEntry(chars, ACAMERA_SENSOR_ORIENTATION, &entry);
            sensor_orientation = entry.data.i32[0];
            ACameraMetadata_free(chars);
            break;
        }
        ACameraMetadata_free(chars);
    }

    AImageReader_new(width, height, AIMAGE_FORMAT_YUV_420_888, 2, &image_reader);
    AImageReader_setImageListener(image_reader, &reader_listener);
    AImageReader_getWindow(image_reader, &image_reader_surface);

    ACameraManager_openCamera(camera_manager, selected_id, &device_callbacks, &camera_device);
    ACameraManager_deleteCameraIdList(id_list);

    {
        std::lock_guard<std::mutex> lock(window_mutex);
        viewfinder_window = ANativeWindow_fromSurface(getJNIEnv(), vSurface);
        if (mSurface) mlkit_window = ANativeWindow_fromSurface(getJNIEnv(), mSurface);
        if (viewfinder_window) ANativeWindow_setBuffersGeometry(viewfinder_window, 0, 0, WINDOW_FORMAT_RGBA_8888);
        if (mlkit_window) ANativeWindow_setBuffersGeometry(mlkit_window, 0, 0, WINDOW_FORMAT_RGBA_8888);
    }

    ACaptureSessionOutputContainer_create(&output_container);
    ACaptureSessionOutput_create(image_reader_surface, &image_reader_session_output);
    ACaptureSessionOutputContainer_add(output_container, image_reader_session_output);
    ACameraDevice_createCaptureSession(camera_device, output_container, &session_callbacks, &capture_session);

    ACameraDevice_createCaptureRequest(camera_device, TEMPLATE_PREVIEW, &capture_request);
    ACameraOutputTarget_create(image_reader_surface, &image_reader_target);
    ACaptureRequest_addTarget(capture_request, image_reader_target);
    
    is_running = true;
    ACameraCaptureSession_setRepeatingRequest(capture_session, nullptr, 1, &capture_request, nullptr);
    return true;
}

void NativeCamera::close() {
    is_running = false;
    if (capture_session) {
        ACameraCaptureSession_stopRepeating(capture_session);
        ACameraCaptureSession_close(capture_session);
        capture_session = nullptr;
    }
    if (camera_device) {
        ACameraDevice_close(camera_device);
        camera_device = nullptr;
    }
    if (image_reader) {
        AImageReader_delete(image_reader);
        image_reader = nullptr;
    }
    std::lock_guard<std::mutex> lock(window_mutex);
    if (viewfinder_window) { ANativeWindow_release(viewfinder_window); viewfinder_window = nullptr; }
    if (mlkit_window) { ANativeWindow_release(mlkit_window); mlkit_window = nullptr; }
    if (capture_request) { ACaptureRequest_free(capture_request); capture_request = nullptr; }
    if (image_reader_target) { ACameraOutputTarget_free(image_reader_target); image_reader_target = nullptr; }
    if (image_reader_session_output) { ACaptureSessionOutput_free(image_reader_session_output); image_reader_session_output = nullptr; }
    if (output_container) { ACaptureSessionOutputContainer_free(output_container); output_container = nullptr; }
    image_reader_surface = nullptr;
}

void NativeCamera::on_image(const cv::Mat& rgba) {}

void NativeCamera::onImageAvailable(void* context, AImageReader* reader) {
    auto* self = static_cast<NativeCamera*>(context);
    if (!self->is_running) {
        AImage* image = nullptr;
        if (AImageReader_acquireLatestImage(reader, &image) == AMEDIA_OK && image) AImage_delete(image);
        return;
    }

    AImage* image = nullptr;
    if (AImageReader_acquireLatestImage(reader, &image) != AMEDIA_OK || !image) return;

    cv::Mat rgba = aImgToMat(image);
    AImage_delete(image);
    if (rgba.empty()) return;

    // 1. Preprocessing (Rotate then Resize-then-Crop)
    cv::Mat rotated;
    switch (self->sensor_orientation) {
        case 90: cv::rotate(rgba, rotated, cv::ROTATE_90_CLOCKWISE); break;
        case 180: cv::rotate(rgba, rotated, cv::ROTATE_180); break;
        case 270: cv::rotate(rgba, rotated, cv::ROTATE_90_COUNTERCLOCKWISE); break;
        default: rotated = rgba; break;
    }
    if (self->camera_facing == 0) cv::flip(rotated, rotated, 1);

    double scale = 640.0 / std::min(rotated.cols, rotated.rows);
    cv::Mat scaled;
    cv::resize(rotated, scaled, cv::Size(), scale, scale);
    int cx = (scaled.cols - 640) / 2;
    int cy = (scaled.rows - 640) / 2;
    cv::Mat cropped = scaled(cv::Rect(cx, cy, 640, 640)).clone();

    float latency = 0;
    int mode = getNativeMode();

    // 2. AI Mode: Composite BBoxes in NDK
    if (mode == 1 && detector) {
        auto start = std::chrono::steady_clock::now();
        auto results = detector->detect(cropped, getNativeConf(), getNativeIoU(), getNativeClasses());
        
        // Composite directly on 'cropped' Mat
        for (const auto& res : results) {
            cv::Rect rect(res.x, res.y, res.width, res.height);
            cv::rectangle(cropped, rect, cv::Scalar(0, 255, 0, 255), 2);
            
            std::string label = res.label + " " + std::to_string((int)(res.confidence * 100)) + "%";
            int baseLine;
            cv::Size labelSize = cv::getTextSize(label, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);
            cv::rectangle(cropped, cv::Rect(res.x, res.y - labelSize.height, labelSize.width, labelSize.height + baseLine), cv::Scalar(0, 255, 0, 255), -1);
            cv::putText(cropped, label, cv::Point(res.x, res.y), cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(255, 255, 255, 255), 1);
        }

        auto end = std::chrono::steady_clock::now();
        latency = (float)std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    }

    // 3. Apply Filters (Available in all modes)
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

    // 4. Rendering
    {
        std::lock_guard<std::mutex> lock(self->window_mutex);
        if (self->viewfinder_window && self->is_running) {
            ANativeWindow_Buffer buf;
            if (ANativeWindow_lock(self->viewfinder_window, &buf, nullptr) == 0) {
                if (buf.width > 0 && buf.height > 0 && buf.bits != nullptr) {
                    cv::Mat dst(buf.height, buf.width, CV_8UC4, buf.bits, buf.stride * 4);
                    dst.setTo(cv::Scalar(0, 0, 0, 255));
                    double rScale = std::min((double)buf.width / cropped.cols, (double)buf.height / cropped.rows);
                    int nw = (int)(cropped.cols * rScale);
                    int nh = (int)(cropped.rows * rScale);
                    cv::Mat renderDst = dst(cv::Rect((buf.width - nw) / 2, (buf.height - nh) / 2, nw, nh));
                    cv::resize(cropped, renderDst, renderDst.size());
                }
                ANativeWindow_unlockAndPost(self->viewfinder_window);
            }
        }
        // ML Kit stream (Raw, no NDK composites)
        if (self->mlkit_window && self->is_running && mode == 2) {
            ANativeWindow_Buffer buf;
            if (ANativeWindow_lock(self->mlkit_window, &buf, nullptr) == 0) {
                cv::Mat dst(buf.height, buf.width, CV_8UC4, buf.bits, buf.stride * 4);
                cv::resize(cropped, dst, dst.size());
                ANativeWindow_unlockAndPost(self->mlkit_window);
            }
        }
    }

    // 5. Update Metrics
    self->frame_count++;
    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - self->last_fps_time).count();
    if (elapsed > 1000) {
        self->current_fps = (float)self->frame_count * 1000.0f / (float)elapsed;
        self->frame_count = 0;
        self->last_fps_time = now;
    }
    updatePerfMetrics(self->current_fps, latency, 640, 640);
}

void NativeCamera::onDeviceDisconnected(void* context, ACameraDevice* device) {}
void NativeCamera::onDeviceError(void* context, ACameraDevice* device, int error) {}
void NativeCamera::onSessionClosed(void* context, ACameraCaptureSession* session) {}
void NativeCamera::onSessionReady(void* context, ACameraCaptureSession* session) {}
void NativeCamera::onSessionActive(void* context, ACameraCaptureSession* session) {}
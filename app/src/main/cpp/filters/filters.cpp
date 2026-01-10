#include "filters.h"
#include <vector>

using namespace cv;
using namespace std;

void applyBeauty(Mat& src) {
    if (src.empty()) return;
    Mat bgr;
    if (src.channels() == 4) cvtColor(src, bgr, COLOR_RGBA2BGR);
    else bgr = src.clone();

    Mat smoothed;
    bilateralFilter(bgr, smoothed, 9, 75, 75);
    Mat sharpened;
    addWeighted(smoothed, 1.5, smoothed, -0.5, 0, sharpened);

    if (src.channels() == 4) cvtColor(sharpened, src, COLOR_BGR2RGBA);
    else sharpened.copyTo(src);
}

void applyDehaze(Mat& src) {
    if (src.empty()) return;
    Mat lab;
    if (src.channels() == 4) cvtColor(src, lab, COLOR_RGBA2BGR); else lab = src.clone();
    cvtColor(lab, lab, COLOR_BGR2Lab);
    vector<Mat> lab_planes;
    split(lab, lab_planes);
    Ptr<CLAHE> clahe = createCLAHE();
    clahe->setClipLimit(2.0);
    clahe->apply(lab_planes[0], lab_planes[0]);
    merge(lab_planes, lab);
    Mat result;
    cvtColor(lab, result, COLOR_Lab2BGR);
    if (src.channels() == 4) cvtColor(result, src, COLOR_BGR2RGBA); else result.copyTo(src);
}

void applyUnderwater(Mat& src) {
    if (src.empty()) return;
    Mat bgr;
    if (src.channels() == 4) cvtColor(src, bgr, COLOR_RGBA2BGR); else bgr = src; 
    vector<Mat> channels;
    split(bgr, channels);
    add(channels[2], Scalar(40), channels[2]); 
    merge(channels, bgr);
    if (src.channels() == 4) cvtColor(bgr, src, COLOR_BGR2RGBA);
}

// Optimized applyStage to achieve 30fps
void applyStage(Mat& src) {
    if (src.empty()) return;
    
    int rows = src.rows, cols = src.cols;
    static Mat cachedMask;
    static int cachedRows = -1, cachedCols = -1;
    
    if (rows != cachedRows || cols != cachedCols) {
        Mat maskFloat = Mat::zeros(rows, cols, CV_32F);
        circle(maskFloat, Point(cols/2, rows/2), min(rows, cols)/2, Scalar(1.0), -1);
        GaussianBlur(maskFloat, maskFloat, Size(0,0), min(rows, cols)/4);
        // Convert to 8-bit for faster multiplication
        maskFloat.convertTo(cachedMask, CV_8U, 255.0);
        cachedRows = rows;
        cachedCols = cols;
    }

    if (src.channels() == 4) {
        vector<Mat> chans;
        split(src, chans);
        for(int i = 0; i < 3; i++) { 
            multiply(chans[i], cachedMask, chans[i], 1.0/255.0);
        }
        merge(chans, src);
    } else {
        vector<Mat> chans;
        split(src, chans);
        for(int i = 0; i < chans.size(); i++) {
            multiply(chans[i], cachedMask, chans[i], 1.0/255.0);
        }
        merge(chans, src);
    }
}

void applyGray(Mat& src) {
    if(src.channels()==4) {
        Mat gray;
        cvtColor(src, gray, COLOR_RGBA2GRAY);
        cvtColor(gray, src, COLOR_GRAY2RGBA);
    } else if(src.channels()==3) {
        Mat gray;
        cvtColor(src, gray, COLOR_BGR2GRAY);
        cvtColor(gray, src, COLOR_GRAY2BGR);
    }
}

void applyHistEq(Mat& src) {
    if (src.empty()) return;
    Mat ycrcb;
    
    if(src.channels() == 4) {
        Mat bgr;
        cvtColor(src, bgr, COLOR_RGBA2BGR);
        cvtColor(bgr, ycrcb, COLOR_BGR2YCrCb);
        vector<Mat> c; split(ycrcb, c);
        equalizeHist(c[0], c[0]);
        merge(c, ycrcb);
        cvtColor(ycrcb, bgr, COLOR_YCrCb2BGR);
        cvtColor(bgr, src, COLOR_BGR2RGBA);
    } else {
        cvtColor(src, ycrcb, COLOR_BGR2YCrCb);
        vector<Mat> c; split(ycrcb, c);
        equalizeHist(c[0], c[0]);
        merge(c, ycrcb);
        cvtColor(ycrcb, src, COLOR_YCrCb2BGR);
    }
}

void applyBinary(Mat& src) {
    if (src.empty()) return;
    Mat g; 
    if(src.channels()==4) cvtColor(src, g, COLOR_RGBA2GRAY); 
    else cvtColor(src, g, COLOR_BGR2GRAY);
    
    threshold(g, g, 128, 255, THRESH_BINARY);
    
    if(src.channels()==4) cvtColor(g, src, COLOR_GRAY2RGBA);
    else cvtColor(g, src, COLOR_GRAY2BGR);
}

void applyMorphOpen(Mat& src) {
    if (src.empty()) return;
    morphologyEx(src, src, MORPH_OPEN, getStructuringElement(MORPH_RECT, Size(5,5)));
}

void applyMorphClose(Mat& src) {
    if (src.empty()) return;
    morphologyEx(src, src, MORPH_CLOSE, getStructuringElement(MORPH_RECT, Size(5,5)));
}

void applyBlur(Mat& src) {
    if (src.empty()) return;
    GaussianBlur(src, src, Size(15,15), 0);
}

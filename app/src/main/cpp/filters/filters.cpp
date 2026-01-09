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

void applyStage(Mat& src) {
    if (src.empty()) return;
    Mat bgr;
    if (src.channels() == 4) cvtColor(src, bgr, COLOR_RGBA2BGR); else bgr = src.clone();
    int rows = bgr.rows, cols = bgr.cols;
    Mat mask = Mat::zeros(rows, cols, CV_32F);
    circle(mask, Point(cols/2, rows/2), min(rows, cols)/2, Scalar(1.0), -1);
    GaussianBlur(mask, mask, Size(0,0), min(rows, cols)/4);
    Mat bgrF; bgr.convertTo(bgrF, CV_32F);
    vector<Mat> chans; split(bgrF, chans);
    for(int i=0; i<3; i++) multiply(chans[i], mask, chans[i]);
    merge(chans, bgrF);
    bgrF.convertTo(bgr, CV_8U);
    if (src.channels() == 4) cvtColor(bgr, src, COLOR_BGR2RGBA); else bgr.copyTo(src);
}

void applyGray(Mat& src) {
    if(src.channels()==4) cvtColor(src, src, COLOR_RGBA2GRAY);
    else if(src.channels()==3) cvtColor(src, src, COLOR_BGR2GRAY);
    cvtColor(src, src, COLOR_GRAY2RGBA); 
}

void applyHistEq(Mat& src) {
    Mat ycrcb;
    if(src.channels()==4) cvtColor(src, src, COLOR_RGBA2BGR);
    cvtColor(src, ycrcb, COLOR_BGR2YCrCb);
    vector<Mat> c; split(ycrcb, c);
    equalizeHist(c[0], c[0]);
    merge(c, ycrcb);
    cvtColor(ycrcb, src, COLOR_YCrCb2BGR);
    cvtColor(src, src, COLOR_BGR2RGBA);
}

void applyBinary(Mat& src) {
    Mat g; if(src.channels()==4) cvtColor(src, g, COLOR_RGBA2GRAY); else cvtColor(src, g, COLOR_BGR2GRAY);
    threshold(g, g, 128, 255, THRESH_BINARY);
    cvtColor(g, src, COLOR_GRAY2RGBA);
}

void applyMorphOpen(Mat& src) {
    morphologyEx(src, src, MORPH_OPEN, getStructuringElement(MORPH_RECT, Size(5,5)));
}

void applyMorphClose(Mat& src) {
    morphologyEx(src, src, MORPH_CLOSE, getStructuringElement(MORPH_RECT, Size(5,5)));
}

void applyBlur(Mat& src) {
    GaussianBlur(src, src, Size(15,15), 0);
}

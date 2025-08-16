package org.example.model;

import org.bytedeco.opencv.opencv_core.Rect;

/**
 * CaptureState tracks the last time and face rectangle used to decide
 * when to capture/log again for a given label.
 */
public class CaptureState {
    public long lastCaptureTime;
    public Rect lastFaceRect;

    public CaptureState(long time, Rect rect) {
        this.lastCaptureTime = time;
        this.lastFaceRect = rect;
    }
}



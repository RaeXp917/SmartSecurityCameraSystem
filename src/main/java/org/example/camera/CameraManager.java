package org.example.camera;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.util.function.Consumer;

/**
 * CameraManager encapsulates camera initialization (webcam or IP camera)
 * and frame grabbing lifecycle. It emits frames to a consumer callback.
 */
public class CameraManager {

    private OpenCVFrameGrabber grabber;
    private volatile boolean isActive = false;

    public interface GrabberFactory { OpenCVFrameGrabber create(); }

    /**
     * Starts the camera asynchronously and begins delivering frames to the consumer.
     */
    public void startAsync(GrabberFactory factory, Consumer<Frame> onFrame, Consumer<Exception> onError) {
        stop();
        new Thread(() -> {
            try {
                grabber = factory.create();
                grabber.start();
                isActive = true;
                while (isActive) {
                    Frame frame = grabber.grab();
                    if (frame != null && frame.image != null) {
                        onFrame.accept(frame);
                    }
                }
            } catch (Exception e) {
                onError.accept(e);
            }
        }, "camera-thread").start();
    }

    /** Stops the camera and releases resources. Safe to call multiple times. */
    public void stop() {
        isActive = false;
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (FrameGrabber.Exception e) {
                throw new RuntimeException(e);
            } finally {
                grabber = null;
            }
        }
    }

    public OpenCVFrameGrabber getGrabber() { return grabber; }
}



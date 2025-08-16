package org.example.video;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.opencv_core.Mat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;

/**
 * VideoRecorder writes buffered Mats to an MP4 file with H264 encoding.
 */
public class VideoRecorder {
    private final OpenCVFrameConverter.ToMat toMatConverter = new OpenCVFrameConverter.ToMat();
    private final AtomicBoolean isRecording;
    private final String recordingsDir;

    public VideoRecorder(AtomicBoolean isRecording, String recordingsDir) {
        this.isRecording = isRecording;
        this.recordingsDir = recordingsDir;
    }

    public void recordBufferedMats(List<Mat> matsToRecord, OpenCVFrameGrabber activeGrabber, Runnable onDone, java.util.function.Consumer<String> onStatus) {
        if (matsToRecord.isEmpty()) {
            onStatus.accept("Error: Frame buffer is empty. Nothing to record.");
            isRecording.set(false);
            return;
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String outputPath = java.nio.file.Paths.get(recordingsDir, "clip_" + timestamp + ".mp4").toString();
        try (FrameRecorder recorder = new FFmpegFrameRecorder(outputPath, activeGrabber.getImageWidth(), activeGrabber.getImageHeight(), 0)) {
            recorder.setVideoCodec(AV_CODEC_ID_H264);
            recorder.setFrameRate(activeGrabber.getFrameRate());
            recorder.setVideoBitrate(2000000);
            recorder.start();
            for (Mat mat : matsToRecord) {
                recorder.record(toMatConverter.convert(mat));
            }
            onStatus.accept("Status: Clip saved to " + outputPath);
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
            onStatus.accept("Error: Failed to save video clip.");
        } finally {
            for (Mat mat : matsToRecord) {
                mat.release();
            }
            onDone.run();
            isRecording.set(false);
        }
    }
}



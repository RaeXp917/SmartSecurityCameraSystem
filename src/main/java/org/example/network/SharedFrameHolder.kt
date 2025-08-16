package org.example.network

import java.util.concurrent.atomic.AtomicReference

/**
 * A thread-safe holder for the latest camera frame, compressed as a JPEG.
 * This acts as a bridge between the Java camera thread (producer) and
 * the Ktor web server threads (consumers).
 */
object SharedFrameHolder {
    // AtomicReference ensures that reads/writes of the frame are atomic.
    private val latestFrame = AtomicReference<ByteArray?>(null)

    /**
     * Called from the Java camera loop to update the current frame.
     */
    @JvmStatic
    fun updateFrame(newFrame: ByteArray) {
        latestFrame.set(newFrame)
    }

    /**
     * Called from the Ktor endpoint to get the latest frame for streaming.
     */
    fun getFrame(): ByteArray? {
        return latestFrame.get()
    }
}
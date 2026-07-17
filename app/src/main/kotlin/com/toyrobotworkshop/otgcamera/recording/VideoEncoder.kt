package com.toyrobotworkshop.otgcamera.recording

import android.media.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer

/**
 * H.264 video encoder using MediaCodec + MediaMuxer.
 *
 * Two modes:
 * - **Surface mode** (Camera2): MediaCodec provides an input Surface that the camera
 *   writes directly to. Zero-copy, hardware-accelerated path.
 * - **Buffer mode** (UVC): Raw YUV frames are fed into MediaCodec input buffers via callback.
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    private val outputPath: String,
) {

    private val tag = "VideoEncoder"

    // Codec + muxer
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1

    // Handler thread for codec operations
    private var handlerThread: HandlerThread? = null
    private var handler: android.os.Handler? = null

    // State
    private var isStarted = false
    private var isMuxing = false
    private var isEncoding = false

    // Surface mode (Camera2)
    private var inputSurface: android.view.Surface? = null

    // Buffer mode (UVC) — callback for feeding frames
    private var frameCallback: ((ByteBuffer) -> Unit)? = null

    // Timestamps
    private var startTimestampNs: Long = 0
    private var frameCount = 0

    /**
     * Initialize the encoder in Surface mode (Camera2 path).
     * Returns the input Surface that the camera should target.
     */
    fun initSurfaceMode(): android.view.Surface? {
        return try {
            handlerThread = HandlerThread("VideoEncoder").also { it.start() }
            handler = android.os.Handler(handlerThread!!.looper)

            val mime = "video/avc"
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            mediaCodec = MediaCodec.createEncoderByType(mime).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            isEncoding = true
            Log.d(tag, "Encoder initialized in Surface mode: ${width}x${height}@${bitrate / 1000}kbps")
            inputSurface
        } catch (e: Exception) {
            Log.e(tag, "Failed to init encoder (surface mode)", e)
            stop()
            null
        }
    }

    /**
     * Initialize the encoder in Buffer mode (UVC path).
     * Returns a callback that the caller uses to feed YUV frames.
     */
    fun initBufferMode(): ((ByteBuffer) -> Unit)? {
        return try {
            handlerThread = HandlerThread("VideoEncoder").also { it.start() }
            handler = android.os.Handler(handlerThread!!.looper)

            val mime = "video/avc"
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            mediaCodec = MediaCodec.createEncoderByType(mime).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            isEncoding = true

            // Return a callback that feeds frames into the encoder
            val callback: (ByteBuffer) -> Unit = { frameBuffer ->
                feedFrame(frameBuffer)
            }
            frameCallback = callback

            Log.d(tag, "Encoder initialized in Buffer mode: ${width}x${height}@${bitrate / 1000}kbps")
            callback
        } catch (e: Exception) {
            Log.e(tag, "Failed to init encoder (buffer mode)", e)
            stop()
            null
        }
    }

    /**
     * Start muxing — creates the MP4 file and begins writing.
     * Must be called after initSurfaceMode or initBufferMode.
     */
    fun startMuxing() {
        if (isMuxing) return

        try {
            mediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            isMuxing = true
            startTimestampNs = System.nanoTime()
            Log.d(tag, "Muxer started: $outputPath")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start muxer", e)
            stop()
        }
    }

    /**
     * Stop encoding and finalize the MP4 file.
     */
    fun stop() {
        isEncoding = false

        // Signal EOS to the codec
        mediaCodec?.signalEndOfInputStream()

        // Drain remaining output buffers
        drainEncoder(flush = true)

        // Stop and release
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaCodec = null

        mediaMuxer?.stop()
        mediaMuxer?.release()
        mediaMuxer = null

        inputSurface?.release()
        inputSurface = null

        handler?.looper?.quitSafely()
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null

        isMuxing = false
        Log.d(tag, "Encoder stopped. Frames encoded: $frameCount")
    }

    /**
     * Feed a YUV frame into the encoder (buffer mode only).
     */
    private fun feedFrame(frameBuffer: ByteBuffer) {
        val codec = mediaCodec ?: return

        // Get an input buffer
        val inputBufferId = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferId >= 0) {
            val inputBuffer = codec.getInputBuffer(inputBufferId)
            inputBuffer?.clear()
            inputBuffer?.put(frameBuffer)

            val timestampUs = (System.nanoTime() - startTimestampNs) / 1000
            codec.queueInputBuffer(inputBufferId, 0, frameBuffer.remaining(), timestampUs, 0)
            frameCount++
        }
    }

    /**
     * Drain output buffers and write to muxer.
     * Call this periodically in surface mode; called automatically in buffer mode.
     */
    fun drainEncoder(flush: Boolean = false) {
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return

        var info = MediaCodec.BufferInfo()

        do {
            val outputBufferId = codec.dequeueOutputBuffer(info, TIMEOUT_US)

            if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // First frame — create the video track
                if (videoTrackIndex < 0) {
                    val format = codec.outputFormat
                    videoTrackIndex = muxer.addTrack(format)
                    muxer.start()
                    Log.d(tag, "Video track added at index $videoTrackIndex")
                }
            } else if (outputBufferId >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferId)
                if (outputBuffer != null && info.size > 0) {
                    // Handle renderer synchronization — required for Audio and Video
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0
                    }
                    muxer.writeSampleData(videoTrackIndex, outputBuffer, info)
                }
                codec.releaseOutputBuffer(outputBufferId, false)

                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(tag, "EOS received from encoder")
                    break
                }
            } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER && !flush) {
                // No data right now — only break if not flushing
                break
            }
        } while (true)
    }

    companion object {
        private const val TIMEOUT_US = 10_000L // 10 ms (value is in microseconds)
    }
}

package com.qvsorrow.demo.lowlevelvideo.recorder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.qvsorrow.demo.lowlevelvideo.renderer.SurfaceRenderer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
private const val BIT_RATE = 10_000_000 // 10 MBit/s
private const val FRAME_RATE = 60 // 60 FPS
private const val I_FRAME_INTERVAL = 1 // 1 second

private const val CODEC_TIMEOUT_US = 10L

private const val TAG = "VideoRecordingDemo"

class VideoRecorder(private val context: Context) {

    private val bufferInfo = MediaCodec.BufferInfo()

    fun record(width: Int, height: Int, recorder: SurfaceRenderer): Deferred<File?> {
        val deferred = CompletableDeferred<File?>()
        thread {
            val file = executeInternal(width, height, recorder)
            deferred.complete(file)
        }
        return deferred
    }

    private fun executeInternal(width: Int, height: Int, recorder: SurfaceRenderer): File {
        val file = createOutputFile()
        val format = MediaFormat.createVideoFormat(
            VIDEO_MIME_TYPE,
            width,
            height,
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_OPERATING_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_PRIORITY, 1 /* best effort */) // 0 is realtime
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val encoderName = codecList.findEncoderForFormat(format)
        val codecInfo = codecList.codecInfos.find { it.name == encoderName }
        Log.d(TAG, "Selected encoder: $encoderName")
        Log.d(TAG, "CodecInfo: ${codecInfo?.supportedTypes?.toList()}")


        val capabilities = codecInfo?.getCapabilitiesForType(VIDEO_MIME_TYPE)
        capabilities?.encoderCapabilities?.let { encoderCapabilities ->
            var mode = 0
            if (encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)) {
                Log.d(TAG, "Supports CQ")
                mode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ
            }
            if (encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) {
                Log.d(TAG, "Supports VBR")
                mode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
            }
            if (encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
                Log.d(TAG, "Supports CBR")
                mode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            }
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, mode)
        }

        Log.i(TAG, "Output format: $format")

        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val encoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)


        val surface = encoder.createInputSurface()

        encoder.start()

        recorder.onPrepare(surface)
        recorder.onSizeChanged(surface, width, height)

        var isSignaledEos = false
        var isPrepare = true
        var trackId = -1

        frameLoop@ for (frame in 0..Int.MAX_VALUE) {
            val time = calculateFrameTime(frame, FRAME_RATE)

            writeLoop@ while (true) {
                if (!isSignaledEos) {
                    val isLast = recorder.onRenderFrame(surface, frame, time)
                    if (isLast) {
                        encoder.signalEndOfInputStream()
                        isSignaledEos = true
                    }
                }

                val bufferIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                if (bufferIndex < 0) {
                    continue@writeLoop
                }

                val outputBuffer = encoder.getOutputBuffer(bufferIndex) ?: continue@writeLoop
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG > 0) {
                    bufferInfo.size = 0
                } else {
                    if (isPrepare) {
                        isPrepare = false
                        trackId = muxer.addTrack(encoder.getOutputFormat(bufferIndex))
                        muxer.start()
                    }
                }
                val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0
                if (isEos) {
                    encoder.releaseOutputBuffer(bufferIndex, false)
                    break@frameLoop
                }

                if (!isPrepare && bufferInfo.size > 0) {
                    if (bufferInfo.presentationTimeUs < 0) {
                        bufferInfo.presentationTimeUs = time.inWholeMicroseconds
                    }
                    muxer.writeSampleData(trackId, outputBuffer, bufferInfo)
                }
                encoder.releaseOutputBuffer(bufferIndex, false)

                break@writeLoop
            }

        }

        recorder.onRelease()
        surface.release()

        try {
            encoder.stop()
        } catch (e: Exception) {
            Log.w(TAG, e)
        } finally {
            encoder.release()
        }
        try {
            muxer.stop()
        } catch (e: Exception) {
            Log.w(TAG, e)
        } finally {
            muxer.release()
        }
        return file
    }

    private fun calculateFrameTime(frame: Int, fps: Int): Duration {
        return 1.seconds / fps * frame
    }

    private fun createOutputFile(): File {
        val dir = context.filesDir.resolve("recorded_video")
        dir.mkdirs()
        val file = File(dir, "video_${UUID.randomUUID()}.mp4")
        file.createNewFile()
        return file
    }

}


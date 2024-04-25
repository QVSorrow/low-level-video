package com.qvsorrow.demo.lowlevelvideo.transcoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.qvsorrow.demo.lowlevelvideo.core.TrackInfo
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

private const val TAG = "VideoTranscoder"

private const val CODEC_TIMEOUT_US = 10L

class VideoTranscoder(private val context: Context) {


    fun execute(
        input: Uri,
        configuration: OutputConfiguration,
        listener: TranscoderProgressListener? = null
    ): File {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, input, null)
        val tracks = List(extractor.trackCount) { TrackInfo(it, extractor.getTrackFormat(it)) }
        val video = tracks.filter { it.mimeStartsWith("video/") }

        if (video.isEmpty()) {
            error("No video tracks")
        }
        val track = video.first()

        Log.d(TAG, "Selected track: ${track.format}")
        extractor.selectTrack(track.id)


        val decoder = MediaCodec.createDecoderByType(track.mime)

        val output = createOutputFile(configuration)
        if (!output.exists()) {
            output.createNewFile()
        }
        val inputWidth = track.format.getInteger(MediaFormat.KEY_WIDTH)
        val inputHeight = track.format.getInteger(MediaFormat.KEY_HEIGHT)

        val format = MediaFormat.createVideoFormat(
            configuration.videoMimeType,
            inputWidth,
            inputHeight,
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, configuration.bitrate)
            setInteger(MediaFormat.KEY_PRIORITY, 1 /* best effort */) // 0 is realtime
        }


        Log.d(TAG, "Output format: $format")
        val muxer = MediaMuxer(output.path, configuration.outputFormat)

        val encoder = MediaCodec.createEncoderByType(configuration.videoMimeType)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        decoder.configure(track.format, encoder.createInputSurface(), null, 0)
        decoder.start()

        encoder.start()


        val state = InnerState()

        val extractorThread = thread {
            while (!state.isComplete) {
                if (processDecoderInput(decoder, extractor, listener)) {
                    Thread.yield()
                }
            }
        }

        val decoderOutputThread = thread {
            val bufferInfo = BufferInfo()
            while (!state.isComplete) {
                if (processDecoderOutput(decoder, listener, encoder, bufferInfo)) {
                    Thread.yield()
                }
            }
        }

        val muxerThread = thread {
            val bufferInfo = BufferInfo()
            while (!state.isComplete) {
                if (processEncoderOutput(encoder, muxer, state, listener, bufferInfo)) {
                    Thread.yield()
                }
            }
        }


        extractorThread.join()
        decoderOutputThread.join()
        muxerThread.join()

        decoder.release()
        extractor.release()
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

        return output
    }

    private fun processDecoderInput(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        listener: TranscoderProgressListener?
    ): Boolean {
        val inputBufferIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputBufferIndex < 0) {
            return true
        }
        val buffer = decoder.getInputBuffer(inputBufferIndex) ?: return true
        var flags = 0

        val sampleSize = extractor.readSampleData(buffer, 0)
        val isEOS = sampleSize < 0
        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC > 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (isEOS) {
            flags = flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM
            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, flags)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            return true
        } else {
            decoder.queueInputBuffer(
                inputBufferIndex,
                0,
                sampleSize,
                extractor.sampleTime,
                flags
            )
            listener?.extractTime(extractor.sampleTime)
            extractor.advance()
        }
        return false
    }

    private fun processDecoderOutput(
        decoder: MediaCodec,
        listener: TranscoderProgressListener?,
        encoder: MediaCodec,
        bufferInfo: BufferInfo,
    ): Boolean {
        val outputBufferIndex = decoder.dequeueOutputBuffer(
            bufferInfo,
            CODEC_TIMEOUT_US
        )
        if (outputBufferIndex < 0) {
            return true
        }
        decoder.releaseOutputBuffer(
            outputBufferIndex,
            bufferInfo.presentationTimeUs * 1000,
        )
        listener?.decoderTime(bufferInfo.presentationTimeUs)
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0) {
            encoder.signalEndOfInputStream()
            return true
        }
        return false
    }

    private fun processEncoderOutput(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        state: InnerState,
        listener: TranscoderProgressListener?,
        bufferInfo: BufferInfo,
    ): Boolean {

        val bufferIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
        if (bufferIndex < 0) {
            return true
        }

        val outputBuffer = encoder.getOutputBuffer(bufferIndex) ?: return true
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG > 0) {
            bufferInfo.size = 0
        } else {
            if (state.isMuxerPrepare) {
                state.isMuxerPrepare = false
                state.trackId = muxer.addTrack(encoder.getOutputFormat(bufferIndex))
                muxer.start()
            }
        }
        val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0
        if (isEos) {
            encoder.releaseOutputBuffer(bufferIndex, false)
            state.isComplete = true
            return true
        }

        if (!state.isMuxerPrepare && bufferInfo.size > 0) {
            muxer.writeSampleData(state.trackId, outputBuffer, bufferInfo)
            listener?.muxerTime(bufferInfo.presentationTimeUs)
        }
        encoder.releaseOutputBuffer(bufferIndex, false)
        return false
    }

    private fun createOutputFile(configuration: OutputConfiguration): File {
        val dir = context.filesDir.resolve("recorded_video")
        dir.mkdirs()
        val extension = when (configuration.outputFormat) {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 -> "mp4"
            MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM -> "webm"
            MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP -> "3gp"
            MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF -> "heif"
            MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG -> "ogg"
            else -> "mp4"
        }
        val file = File(dir, "video_${UUID.randomUUID()}.$extension")
        file.createNewFile()
        return file
    }

}

private class InnerState {
    var isMuxerPrepare: Boolean = true
    var trackId: Int = -1

    @Volatile
    var isComplete: Boolean = false
}


data class OutputConfiguration(
    val videoMimeType: String = MediaFormat.MIMETYPE_VIDEO_HEVC,
    val bitrate: Int = 50_000,
    val outputFormat: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
)


interface TranscoderProgressListener {
    fun extractTime(time: Long)
    fun decoderTime(time: Long)
    fun muxerTime(time: Long)
}


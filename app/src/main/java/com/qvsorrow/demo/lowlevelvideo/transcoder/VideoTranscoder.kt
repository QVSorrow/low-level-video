package com.qvsorrow.demo.lowlevelvideo.transcoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.qvsorrow.demo.lowlevelvideo.core.TrackInfo
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "VideoTranscoder"

private const val CODEC_TIMEOUT_US = 10L

class VideoTranscoder(private val context: Context) {

    private val bufferInfo = MediaCodec.BufferInfo()

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
            (inputWidth * configuration.scale).roundToInt(),
            (inputHeight * configuration.scale).roundToInt(),
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, configuration.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, configuration.fps)
            setInteger(MediaFormat.KEY_OPERATING_RATE, configuration.fps)
            setInteger(MediaFormat.KEY_PRIORITY, 1 /* best effort */) // 0 is realtime
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, configuration.iframeInterval)
        }


        Log.d(TAG, "Output format: $format")
        val muxer = MediaMuxer(output.path, configuration.outputFormat)

        val encoder = MediaCodec.createEncoderByType(configuration.videoMimeType)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        decoder.configure(track.format, encoder.createInputSurface(), null, 0)
        decoder.start()

        encoder.start()


        var isPrepare = true
        var trackId = -1

        mainLoop@ while (true) {

            decoderInput@ while (true) {
                val inputBufferIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputBufferIndex < 0) {
                    break@decoderInput
                }
                val buffer = decoder.getInputBuffer(inputBufferIndex) ?: break@decoderInput
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
                    break@decoderInput
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
            }

            decoderOutput@ while (true) {
                val outputBufferIndex = decoder.dequeueOutputBuffer(
                    bufferInfo,
                    CODEC_TIMEOUT_US
                )
                if (outputBufferIndex < 0) {
                    break@decoderOutput
                }
                decoder.releaseOutputBuffer(
                    outputBufferIndex,
                    bufferInfo.presentationTimeUs * 1000,
                )
                listener?.decoderTime(bufferInfo.presentationTimeUs)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0) {
                    encoder.signalEndOfInputStream()
                    break@decoderOutput
                    // DONE !
                }
            }

            decoderOutput@ while (true) {
                val bufferIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                if (bufferIndex < 0) {
                    break@decoderOutput
                }
                listener?.encoderTime(bufferInfo.presentationTimeUs)

                val outputBuffer = encoder.getOutputBuffer(bufferIndex) ?: break@decoderOutput
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
                    break@mainLoop
                }

                if (!isPrepare && bufferInfo.size > 0) {
//                    if (bufferInfo.presentationTimeUs < 0) {
//                        bufferInfo.presentationTimeUs = 0
//                    }
                    muxer.writeSampleData(trackId, outputBuffer, bufferInfo)
                    listener?.muxerTime(bufferInfo.presentationTimeUs)
                }
                encoder.releaseOutputBuffer(bufferIndex, false)
            }
        }

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


data class OutputConfiguration(
    val videoMimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    val bitrate: Int = 10_000_000,
    val fps: Int = 30,
    val iframeInterval: Int = 1,
    val scale: Float = 1f,
    val outputFormat: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
)


interface TranscoderProgressListener {
    fun extractTime(time: Long)
    fun decoderTime(time: Long)
    fun encoderTime(time: Long)
    fun muxerTime(time: Long)
}


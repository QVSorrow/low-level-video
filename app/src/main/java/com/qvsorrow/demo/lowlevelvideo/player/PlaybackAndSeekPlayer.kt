package com.qvsorrow.demo.lowlevelvideo.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.compose.ui.unit.IntSize
import com.qvsorrow.demo.lowlevelvideo.core.TrackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "PlaybackAndSeekPlayer"
private const val CODEC_TIMEOUT_US = 10L
private const val DROP_THRESHOLD_NS = 100_000L


class PlaybackAndSeekPlayer(
    private val context: Context,
    private val uri: Uri,
) : Player {

    private val renderThread: HandlerThread = HandlerThread(TAG)
    private val handler: Handler

    private val _time = MutableStateFlow(0L)
    private val _totalTime = MutableStateFlow(1L)
    private val _dimensions = MutableStateFlow(IntSize(1, 1))
    private val _playing = MutableStateFlow(false)
    private val bufferInfo = MediaCodec.BufferInfo()
    @Volatile
    private var isPrepared: Boolean = false

    private lateinit var track: TrackInfo
    private lateinit var extractor: MediaExtractor
    private lateinit var decoder: MediaCodec

    private var startTimestamp: Long = -1

    override val timeUs: StateFlow<Long> = _time
    override val totalTimeUs: StateFlow<Long> = _totalTime
    override val dimensions: StateFlow<IntSize> = _dimensions
    override val isPlaying: StateFlow<Boolean> = _playing

    init {
        renderThread.start()
        handler = Handler(renderThread.looper)
    }

    override fun prepare(surface: Surface) {
        handler.post {
            prepareMediaExtractor()
            prepareDecoder(surface)
            startTimestamp = -1
            isPrepared = true
        }
    }

    override fun play() {
        handler.post {
            _playing.value = true
            startTimestamp = -1
            playInternal()
        }
    }

    override fun pause() {
        handler.post {
            _playing.value = false
            handler.removeCallbacksAndMessages(null)
        }
    }

    override fun seekTo(positionUs: Long) {
        if (!isPrepared) return
        handler.post {
            extractor.seekTo(positionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            startTimestamp = System.nanoTime() - positionUs * 1000
        }
    }

    override fun release() {
        isPrepared = false
        handler.removeCallbacksAndMessages(null)
        handler.postAtFrontOfQueue {
            decoder.release()
            extractor.release()
        }
    }

    private fun prepareDecoder(surface: Surface) {
        decoder = MediaCodec.createDecoderByType(track.mime)
        decoder.configure(track.format, surface, null, 0)

        Log.d(TAG, "Codec name: ${decoder.name}")
        decoder.start()
        decoder.setOnFrameRenderedListener({ _, presentationTimeUs, _ ->
            _time.value = presentationTimeUs
        }, handler)
    }

    private fun playInternal() {
        processInput()
        processOutput()
    }

    private fun processInput() {
        handler.post {
            try {
                readInputBuffer(decoder, extractor)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            processInput()
        }
    }

    private fun processOutput() {
        handler.post {
            try {
                readOutputBuffer(decoder, bufferInfo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            processOutput()
        }
    }


    private fun prepareMediaExtractor() {
        extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val tracks = List(extractor.trackCount) { TrackInfo(it, extractor.getTrackFormat(it)) }
        val video = tracks.filter { it.mimeStartsWith("video/") }

        if (video.isEmpty()) {
            error("No video tracks")
        }
        track = video.first()
        val trackId = track.id

        Log.d(TAG, "Selected track: ${track.format}")
        extractor.selectTrack(trackId)
        _totalTime.value = track.format.getLong(MediaFormat.KEY_DURATION)
        _dimensions.value = IntSize(
            track.format.getInteger(MediaFormat.KEY_WIDTH),
            track.format.getInteger(MediaFormat.KEY_HEIGHT),
        )
    }

    private fun readInputBuffer(
        decoder: MediaCodec,
        extractor: MediaExtractor,
    ) {
        if (!isPrepared) return
        val inputBufferIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputBufferIndex < 0) {
            return
        }
        val buffer = decoder.getInputBuffer(inputBufferIndex) ?: return
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
        } else {
            decoder.queueInputBuffer(
                inputBufferIndex,
                0,
                sampleSize,
                extractor.sampleTime,
                flags
            )
            extractor.advance()
        }
    }

    private fun readOutputBuffer(
        decoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
        if (!isPrepared) return
        val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
        if (outputBufferIndex < 0) {
            return
        }
        // We don't use the outputBuffer, as rendering to Surface is performed by MediaCodec
        val outputBuffer = decoder.getOutputBuffer(outputBufferIndex) ?: return

        val lastFrameTimestamp = bufferInfo.presentationTimeUs * 1000
        if (startTimestamp < 0L) {
            startTimestamp = System.nanoTime() - lastFrameTimestamp
        }
        val renderTime = startTimestamp + lastFrameTimestamp

        if (renderTime + DROP_THRESHOLD_NS < System.nanoTime()) {
            decoder.releaseOutputBuffer(
                outputBufferIndex,
                false,
            )
        } else {
            decoder.releaseOutputBuffer(
                outputBufferIndex,
                renderTime,
            )
        }
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0) {
            decoder.flush()
            startTimestamp = -1
        }
    }
}
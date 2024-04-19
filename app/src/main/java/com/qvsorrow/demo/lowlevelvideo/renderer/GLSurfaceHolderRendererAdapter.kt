package com.qvsorrow.demo.lowlevelvideo.renderer

import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import kotlin.time.Duration.Companion.nanoseconds

class GLSurfaceHolderRendererAdapter(
    private val renderer: SurfaceRenderer,
) : SurfaceHolder.Callback2, Choreographer.FrameCallback {

    private val renderThread: HandlerThread = HandlerThread("GLRendererAdapter")
    private val handler: Handler

    private lateinit var choreographer: Choreographer

    private var frame = 0
    private var surface: Surface? = null

    init {
        renderThread.start()
        handler = Handler(renderThread.looper)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        handler.post {
            choreographer = Choreographer.getInstance()
            renderer.onPrepare(holder.surface)
            surface = holder.surface
            choreographer.postFrameCallback(this)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        handler.postAtFrontOfQueue {
            surface = holder.surface
            renderer.onSizeChanged(holder.surface, width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        handler.removeCallbacksAndMessages(null)
        handler.postAtFrontOfQueue {
            surface = null
            renderer.onRelease()
        }
    }

    override fun surfaceRedrawNeeded(holder: SurfaceHolder) = Unit

    override fun surfaceRedrawNeededAsync(holder: SurfaceHolder, drawingFinished: Runnable) {
        handler.postAtFrontOfQueue {
            val s = surface
            if (s != null) {
                renderFrame(s, System.nanoTime())
            }
            drawingFinished.run()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        renderFrame(surface ?: return, frameTimeNanos)
    }

    private fun renderFrame(surface: Surface, frameTimeNanos: Long) {
        renderer.onRenderFrame(surface, frame, frameTimeNanos.nanoseconds)
        frame += 1
        choreographer.postFrameCallback(this)
    }
}


fun SurfaceRenderer.toSurfaceHolderCallback(): SurfaceHolder.Callback2 {
    return GLSurfaceHolderRendererAdapter(this)
}

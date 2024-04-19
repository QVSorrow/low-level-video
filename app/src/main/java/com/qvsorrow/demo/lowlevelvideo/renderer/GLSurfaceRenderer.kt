package com.qvsorrow.demo.lowlevelvideo.renderer

import android.opengl.GLES20
import android.view.Surface
import com.qvsorrow.demo.lowlevelvideo.core.RenderableEGLContext
import com.qvsorrow.demo.lowlevelvideo.core.createRenderableContext
import kotlin.time.Duration

abstract class GLSurfaceRenderer : SurfaceRenderer() {

    protected open fun initialize() = Unit
    protected open fun changeSize(width: Int, height: Int) = Unit

    /**
     * Draw current frame
     *
     * @param frame     current frame index
     * @param time      current frame time
     *
     * @return `true` if it was the last frame
     */
    protected abstract fun drawFrame(frame: Int, time: Duration): Boolean

    protected open fun cleanup() = Unit


    private lateinit var context: RenderableEGLContext

    @Deprecated("Use GLSurfaceRenderer.initialize()", level = DeprecationLevel.HIDDEN)
    override fun onPrepare(surface: Surface) {
        super.onPrepare(surface)
        context = createRenderableContext(surface) ?: error("Failed to create EGL context")
        context.makeCurrent()
        initialize()
    }

    @Deprecated("Use GLSurfaceRenderer.changeSize()", level = DeprecationLevel.HIDDEN)
    override fun onSizeChanged(surface: Surface, width: Int, height: Int) {
        super.onSizeChanged(surface, width, height)
        changeSize(width, height)
    }

    @Deprecated("Use GLSurfaceRenderer.drawFrame()", level = DeprecationLevel.HIDDEN)
    override fun onRenderFrame(surface: Surface, frame: Int, time: Duration): Boolean {
        super.onRenderFrame(surface, frame, time)

        val result = drawFrame(frame, time)
        context.setPresentationTime(time.inWholeNanoseconds)
        context.swapBuffers()
        return result
    }

    @Deprecated("Use GLSurfaceRenderer.cleanup()", level = DeprecationLevel.HIDDEN)
    override fun onRelease() {
        cleanup()
        context.release()
        super.onRelease()
    }
}
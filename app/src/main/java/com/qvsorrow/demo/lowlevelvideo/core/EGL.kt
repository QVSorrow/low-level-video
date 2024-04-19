package com.qvsorrow.demo.lowlevelvideo.core

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Surface

private val EGL_RECORDABLE_ANDROID = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    EGLExt.EGL_RECORDABLE_ANDROID
} else {
    0x3142
}

private const val TAG = "EGL"

class RenderableEGLContext(
    private val display: EGLDisplay,
    private val context: EGLContext,
    private val surface: EGLSurface,
) {
    fun makeCurrent() {
        EGL14.eglMakeCurrent(display, surface, surface, context)
        checkEGLError("eglMakeCurrent")
    }

    fun swapBuffers(): Boolean {
        return EGL14.eglSwapBuffers(display, surface).also {
            checkEGLError("eglSwapBuffers")
        }
    }

    fun setPresentationTime(nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, nanos)
        checkEGLError("eglPresentationTimeANDROID")
    }

    fun release() {
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
    }
}

fun createRenderableContext(surface: Surface): RenderableEGLContext? {
    val eglDisplay = EGL14.eglGetDisplay(Display.DEFAULT_DISPLAY)
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
        Log.e(TAG, "Failed to get EGLDisplay")
        return null
    }
    val version = IntArray(2)
    val isInitialized = EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
    if (!isInitialized) {
        Log.e(TAG, "Failed to initialize EGL")
        return null
    }

    val configAttrs = intArrayOf(
        EGL14.EGL_LEVEL, 0,
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, // use OpenGL ES2
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_ALPHA_SIZE, 0,
        EGL14.EGL_DEPTH_SIZE, 0,
        EGL14.EGL_STENCIL_SIZE, 0,
        EGL14.EGL_SAMPLE_BUFFERS, 1,
        EGL14.EGL_SAMPLES, 4,  // 4x MSAA
        EGL_RECORDABLE_ANDROID, EGL14.EGL_TRUE,
        EGL14.EGL_NONE,
    )

    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfig = intArrayOf(0)
    EGL14.eglChooseConfig(
        eglDisplay,
        configAttrs,
        0,
        configs,
        0,
        configs.size,
        numConfig,
        0,
    )
    if (checkEGLError("eglChooseConfig") || configs.first() == null) {
        EGL14.eglTerminate(eglDisplay)
        return null
    }

    val contextAttrs = intArrayOf(
        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL14.EGL_NONE,
    )
    val eglContext = EGL14.eglCreateContext(
        eglDisplay,
        configs.first(),
        EGL14.EGL_NO_CONTEXT,
        contextAttrs,
        0,
    )
    if (checkEGLError("eglCreateContext") || eglContext == null) {
        EGL14.eglTerminate(eglDisplay)
        return null
    }
    val surfaceAttrs = intArrayOf(EGL14.EGL_NONE)
    val eglSurface = EGL14.eglCreateWindowSurface(
        eglDisplay,
        configs.first(),
        surface,
        surfaceAttrs,
        0,
    )
    if (checkEGLError("eglCreateWindowSurface") || eglSurface == null) {
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
        return null
    }

    return RenderableEGLContext(eglDisplay, eglContext, eglSurface)
}

private fun checkEGLError(msg: String = ""): Boolean {
    val error: Int = EGL14.eglGetError()
    if (error != EGL14.EGL_SUCCESS) {
        Log.e("EGL Error", "Code: 0x${error.toString(16)} $msg")
        return true
    }
    return false
}

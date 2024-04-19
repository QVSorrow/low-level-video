package com.qvsorrow.demo.lowlevelvideo.renderer

import android.opengl.GLES20
import android.view.Surface
import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animation
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.time.Duration



class ColorAnimationRenderer : GLSurfaceRenderer() {

    private val colors = listOf(
        Color.Red,
        Color.Blue,
        Color.Black,
        Color.Gray,
        Color.White,
        Color.Yellow,
    )

    private var animation: Animation<Color, AnimationVector4D> = TargetBasedAnimation(
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        Color.VectorConverter(ColorSpaces.Srgb),
        colors.random(),
        colors.random(),
    )
    private var animationStart: Duration = Duration.ZERO


    override fun initialize() {
        setAnimation(colors.random())
    }

    override fun drawFrame(frame: Int, time: Duration): Boolean {
        var animationTime = time - animationStart
        if (animation.isFinishedFromNanos(animationTime.inWholeNanoseconds)) {
            setAnimation()
            animationStart = time
            animationTime = Duration.ZERO
        }

        val color = animation.getValueFromNanos(animationTime.inWholeNanoseconds)

        GLES20.glClearColor(color.red, color.green, color.blue, color.alpha)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        return frame >= 600
    }

    private fun setAnimation(from: Color = animation.targetValue) {
        var next: Color = colors.random()
        while (next == from) {
            next = colors.random()
        }
        animation = TargetBasedAnimation(
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
            Color.VectorConverter(ColorSpaces.Srgb),
            from,
            next,
        )
    }
}
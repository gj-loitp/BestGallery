package com.roy.commons.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Shader.TileMode
import android.util.AttributeSet
import android.view.View

class ColorPickerSquare(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var paint: Paint? = null
    private var luar: Shader = LinearGradient(
        /* x0 = */ 0f,
        /* y0 = */ 0f,
        /* x1 = */ 0f,
        /* y1 = */ measuredHeight.toFloat(),
        /* color0 = */ Color.WHITE,
        /* color1 = */ Color.BLACK,
        /* tile = */ Shader.TileMode.CLAMP
    )
    val color = floatArrayOf(1f, 1f, 1f)

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (paint == null) {
            paint = Paint()
            luar = LinearGradient(
                /* x0 = */ 0f,
                /* y0 = */ 0f,
                /* x1 = */ 0f,
                /* y1 = */ measuredHeight.toFloat(),
                /* color0 = */ Color.WHITE,
                /* color1 = */ Color.BLACK,
                /* tile = */ TileMode.CLAMP
            )
        }
        val rgb = Color.HSVToColor(color)
        val dalam = LinearGradient(
            /* x0 = */ 0f,
            /* y0 = */ 0f,
            /* x1 = */ measuredWidth.toFloat(),
            /* y1 = */ 0f,
            /* color0 = */ Color.WHITE,
            /* color1 = */ rgb,
            /* tile = */ TileMode.CLAMP
        )
        val shader = ComposeShader(luar, dalam, PorterDuff.Mode.MULTIPLY)
        paint?.shader = shader
        canvas.drawRect(
            /* left = */ 0f,
            /* top = */ 0f,
            /* right = */ measuredWidth.toFloat(),
            /* bottom = */ measuredHeight.toFloat(),
            /* paint = */ paint!!
        )
    }

    fun setHue(hue: Float) {
        color[0] = hue
        invalidate()
    }
}

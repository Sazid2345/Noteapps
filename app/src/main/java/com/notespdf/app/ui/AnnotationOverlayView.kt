package com.notespdf.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class AnnotationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var annotationBitmap: Bitmap? = null
    private var onTouchCallback: ((MotionEvent) -> Unit)? = null

    fun setAnnotationBitmap(bitmap: Bitmap) {
        annotationBitmap = bitmap
        invalidate()
    }

    fun setOnTouchCallback(callback: (MotionEvent) -> Unit) {
        onTouchCallback = callback
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        annotationBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onTouchCallback?.invoke(event)
        return true
    }
}

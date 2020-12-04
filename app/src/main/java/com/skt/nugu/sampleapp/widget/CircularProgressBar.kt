/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sampleapp.widget

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import com.skt.nugu.sdk.platform.android.ux.template.TemplateUtils.Companion.dpToPixel
import kotlin.math.min

class CircularProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var progress = -1F
    private var paintProgress: Paint = Paint()
    private var paintProgressPath: Paint = Paint()

    private var pointerDrawable: Drawable? = null
    private var drawableWidth = 0
    private var trackPath = Path()
    private var progressPath = Path()
    private var max = 100f
    private var paddingSize = 0f

    private var mPointerPositionXY = FloatArray(2)
    private var offsetInitialized = false
    private var offset = 5f

    fun getProgressDegrees() = getPointerAngle() - offset * 1.25F
    fun getPointerAngle() = ((180f - offset * 2) * (progress / max)) + offset

    init {
        init()
        initPaints()
    }

    private fun initPaints() {
        paintProgress.color = Color.parseColor("#3cb4ff")
        paintProgress.isAntiAlias = true
        paintProgress.style = Paint.Style.STROKE
        paintProgress.strokeCap = Paint.Cap.ROUND
        paintProgress.strokeJoin = Paint.Join.ROUND
        paintProgress.strokeWidth = dpToPixel(context, 6F)
        paintProgress.isDither = true

        paintProgressPath.color = Color.argb(255, 238, 238, 238)
        paintProgressPath.isAntiAlias = true
        paintProgressPath.pathEffect = DashPathEffect(floatArrayOf(0f, 70f), 0F)
        paintProgressPath.style = Paint.Style.STROKE
        paintProgressPath.strokeWidth = dpToPixel(context, 6F)
        paintProgressPath.strokeCap = Paint.Cap.ROUND
        paintProgressPath.strokeJoin = Paint.Join.ROUND
        paintProgressPath.isDither = true
    }

    private fun init() {
        this.setWillNotDraw(false)
        this.setOutlineProvider(null)

        drawableWidth = dpToPixel(context, 40F).toInt()
        paddingSize = dpToPixel(context, 20F)
    }

    fun getProgress() = this.progress
    fun setProgress(progress: Float) {
        this.progress = min(progress, max)
        calculatePath()
        invalidate()
        requestLayout()
    }

    fun setMax(max: Float) {
        this.max = max
        calculatePath()
    }

    fun setDrawable(drawable: Drawable) {
        this.pointerDrawable = drawable
        invalidate()
    }

    // sets the color of the bar (#FF00FF00 - Green by default)
    fun setProgressColor(color: String) {
        paintProgress.color = Color.parseColor(color.replace("##", "#"))
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.let {
            it.drawPath(trackPath, paintProgressPath)
            it.drawPath(progressPath, paintProgress)

            pointerDrawable?.setBounds(
                mPointerPositionXY[0].toInt() - drawableWidth / 2,
                mPointerPositionXY[1].toInt() - drawableWidth / 2,
                mPointerPositionXY[0].toInt() + drawableWidth / 2,
                mPointerPositionXY[1].toInt() + drawableWidth / 2
            )
            pointerDrawable?.draw(it)
        }
        super.onDraw(canvas)
    }

    fun calculateOffset(oval: RectF) {
        if (offsetInitialized) {
            return
        }
        val positionXY = FloatArray(2)
        for (i in 0..10) {
            val calcPath = Path()
            calcPath.addArc(oval, 180F, i.toFloat())

            val pm = PathMeasure(calcPath, false)
            pm.getPosTan(pm.length, positionXY, null)

            if (drawableWidth / 2 <= pm.length) {
                offsetInitialized = true
                offset = i.toFloat()
                break
            }
        }
    }

    private fun calculatePath() {
        val oval = RectF(
            paddingSize,
            paddingSize,
            this.width.toFloat() - paddingSize,
            this.height * 2F - paddingSize
        )
        calculateOffset(oval)
        progressPath = Path()
        progressPath.addArc(oval, 180F, getProgressDegrees())

        trackPath.addArc(oval, 180F, 180f)

        val calcProgressPath = Path()
        calcProgressPath.addArc(oval, 180F, getPointerAngle())
        var pm = PathMeasure(calcProgressPath, false)
        pm.getPosTan(pm.length, mPointerPositionXY, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        calculatePath()
    }

    /**
     * This is called when the view is attached to a window.
     **/
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // This is needed to mimic newer platform behavior.
            // https://stackoverflow.com/a/53625860/715633
            onVisibilityChanged(this, visibility)
        }

        postDelayed({
            requestLayout()
        }, 10)
    }
}
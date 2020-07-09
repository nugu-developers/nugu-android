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
package com.skt.nugu.sdk.platform.android.ux.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.widget.FrameLayout
import com.skt.nugu.sdk.platform.android.ux.R

/**
 * A NuguButton is a circular button designed by Nugu Design guide.
 */
class NuguButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val TYPE_FAB = 0
    private val TYPE_BUTTON = 1
    private val TYPE_FLAGS = intArrayOf(TYPE_FAB, TYPE_BUTTON)

    private val COLOR_BLUE = 0
    private val COLOR_WHITE = 1
    private val COLOR_FLAGS = intArrayOf(COLOR_BLUE, COLOR_WHITE)

    private var nuguButtonType: Int = 0
    private var nuguButtonColor: Int = 0

    private val drawableRes: MutableMap<String, Int> = HashMap()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var numberOfDots = 3
    private val animationHandler = Handler()
    private var activeDotIndex = 0
    private var inactiveColor = 0
        get() {
            return when (nuguButtonColor) {
                COLOR_BLUE -> 0xffffffff.toInt()
                else -> 0xff009dff.toInt()
            }
        }
    private var activeColor = 0
        get() {
            return when (nuguButtonColor) {
                COLOR_BLUE -> 0xff00E688.toInt()
                else -> 0xff16FFA0.toInt()
            }
        }

    private val dotAnimationRunnable by lazy {
        object : Runnable {
            override fun run() {
                if (activeDotIndex == numberOfDots - 1) {
                    activeDotIndex = 0
                } else {
                    activeDotIndex++
                }
                invalidate()
                animationHandler.postDelayed(this, 660)
            }
        }
    }
    fun isFab() = nuguButtonType == TYPE_FAB

    init {
        init(attrs)
        initView()
        loadDrawableRes()
        setupDrawable()
    }

    companion object {
        fun dpToPx(dp: Float, context: Context): Int {
            return (dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
        }
    }

    // initialize custom attributes
    private fun init(attrs: AttributeSet?) {
        context.obtainStyledAttributes(
            attrs, R.styleable.NuguButton, 0, 0
        ).apply {
            nuguButtonType = getInt(R.styleable.NuguButton_types, TYPE_FAB)
            nuguButtonColor = getInt(R.styleable.NuguButton_colors, COLOR_BLUE)
        }.recycle()
    }

    private fun initView() {
        isClickable = true
        isFocusable = true
    }

    private fun loadDrawableRes() {
        drawableRes["btn_blue"] = R.drawable.btn_blue
        drawableRes["btn_blue_pressed"] = R.drawable.btn_blue_pressed
        drawableRes["btn_white"] = R.drawable.btn_white
        drawableRes["btn_white_pressed"] = R.drawable.btn_white_pressed
        drawableRes["btn_disabled"] = R.drawable.btn_disabled
        drawableRes["fab_blue"] = R.drawable.fab_blue
        drawableRes["fab_blue_pressed"] = R.drawable.fab_blue_pressed
        drawableRes["fab_white"] = R.drawable.fab_white
        drawableRes["fab_white_pressed"] = R.drawable.fab_white_pressed
        drawableRes["fab_disabled"] = R.drawable.fab_disabled
        drawableRes["btn_blue_activated"] = R.drawable.btn_blue_activated
        drawableRes["btn_white_activated"] = R.drawable.btn_white_activated
        drawableRes["fab_blue_activated"] = R.drawable.btn_blue_activated
        drawableRes["fab_white_activated"] = R.drawable.btn_white_activated
    }

    /**
     * Set background color
     */
    private fun setupDrawable() {
        val type = when (nuguButtonType) {
            TYPE_FAB -> {
                "fab"
            }
            TYPE_BUTTON -> {
                "btn"
            }
            else -> throw IllegalStateException(nuguButtonType.toString())
        }
        val color = when (nuguButtonColor) {
            COLOR_BLUE -> {
                "blue"
            }
            COLOR_WHITE -> {
                "white"
            }
            else -> throw IllegalArgumentException(nuguButtonColor.toString())
        }

        val states = StateListDrawable()
        // states.setExitFadeDuration(android.R.integer.config_shortAnimTime)
        states.addState(
            intArrayOf(android.R.attr.state_activated),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + color + "_" + "activated"]
                    ?: throw IllegalArgumentException(type + "_" + "activated")
            )
        )

        states.addState(
            intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_pressed),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + color]
                    ?: throw IllegalArgumentException(type + "_" + color)
            )
        )

        states.addState(
            intArrayOf(-android.R.attr.state_enabled),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + "disabled"]
                    ?: throw IllegalArgumentException(type + "_" + "disabled")
            )
        )

        states.addState(
            intArrayOf(android.R.attr.state_pressed),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + color + "_" + "pressed"]
                    ?: throw IllegalArgumentException(type + "_" + color + "_" + "pressed")
            )
        )
        background = states
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
    }

    override fun onDetachedFromWindow() {
        animationHandler.removeCallbacks(dotAnimationRunnable)
        super.onDetachedFromWindow()
    }

    override fun setActivated(activated: Boolean) {
        super.setActivated(activated)
        if (activated && isEnabled) {
            animationHandler.post(dotAnimationRunnable)
        } else {
            animationHandler.removeCallbacks(dotAnimationRunnable)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (isActivated && isEnabled) {
            val dotSize = dpToPx(4F, context)
            val dotSpacing = dpToPx(8F, context)
            // Centering the dots in the middle of the canvas
            val singleDotSize = dotSpacing + dotSize
            val combinedDotSize = singleDotSize * numberOfDots - dotSpacing
            val startingX = ((width - combinedDotSize) / 2)
            val startingY = (height) / 2

            for (i in 0 until numberOfDots) {
                val x = startingX + i * singleDotSize
                paint.color = if (i == activeDotIndex) activeColor else inactiveColor
                canvas?.drawCircle(
                    (x + dotSize / 2).toFloat(),
                    startingY.toFloat(), (dotSize / 2).toFloat(), paint
                )
            }
        }
    }
}
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

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import com.skt.nugu.sdk.platform.android.ux.R

/**
 * A NuguButton is a circular button designed by Nugu Design guide.
 */
class NuguButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        const val TYPE_FAB = 0
        const val TYPE_BUTTON = 1

        @IntDef(TYPE_FAB, TYPE_BUTTON)
        annotation class ButtonTypes

        const val COLOR_BLUE = 0
        const val COLOR_WHITE = 1

        @IntDef(COLOR_BLUE, COLOR_WHITE)
        annotation class ButtonColors

        fun dpToPx(dp: Float, context: Context): Int {
            return (dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
        }
    }

    @ButtonTypes
    private var buttonType: Int = TYPE_FAB

    @ButtonColors
    private var buttonColor: Int = COLOR_BLUE

    private val ICON_MIC = 0
    private val ICON_LOGO = 1

    private val drawableRes: MutableMap<String, Int> = HashMap()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var autoPlay = false
    private var loopPlay = true

    private var animationSet: AnimatorSet? = null

    private var wasAnimatingWhenDetached = false
    private var wasAnimatingWhenNotShown = false
    private var running = false
    private var isInitialized: Boolean = false

    private var numberOfDots = 3
    private val animationHandler = Handler(Looper.getMainLooper())
    private var activeDotIndex = 0
    private var inactiveColor = 0
        get() {
            return when (buttonColor) {
                COLOR_BLUE -> 0xffffffff.toInt()
                else -> 0xff009dff.toInt()
            }
        }
    private var activeColor = 0
        get() {
            return when (buttonColor) {
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

    fun isFab() = buttonType == TYPE_FAB

    init {
        init(attrs)
        initView()
        loadDrawableRes()
        setupImageDrawable()
        setupBackground()
        isInitialized = true
    }

    // initialize custom attributes
    private fun init(attrs: AttributeSet?) {
        context.obtainStyledAttributes(
            attrs, R.styleable.NuguButton, 0, 0
        ).apply {
            buttonType = getInt(R.styleable.NuguButton_types, TYPE_FAB)
            buttonColor = getInt(R.styleable.NuguButton_colors, COLOR_BLUE)

            autoPlay = getBoolean(R.styleable.NuguButton_autoPlay, false)
            loopPlay = getBoolean(R.styleable.NuguButton_loopPlay, true)
        }.recycle()
    }

    private lateinit var imageView: ImageView

    private fun initView() {
        removeAllViews()
        removeAllViewsInLayout()

        val baseLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val size = when (buttonType) {
            TYPE_FAB -> 8f
            else -> 13f
        }

        imageView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(size, context)
            }
        }
        baseLayout.addView(imageView)
        addView(baseLayout)

        isClickable = true
        isFocusable = true
    }

    private fun loadDrawableRes() {
        drawableRes["btn_blue"] = R.drawable.nugu_btn_blue
        drawableRes["btn_blue_pressed"] = R.drawable.nugu_btn_blue_pressed
        drawableRes["btn_white"] = R.drawable.nugu_btn_white
        drawableRes["btn_white_pressed"] = R.drawable.nugu_btn_white_pressed
        drawableRes["btn_disabled"] = R.drawable.nugu_btn_disabled
        drawableRes["fab_blue"] = R.drawable.nugu_fab_blue
        drawableRes["fab_blue_pressed"] = R.drawable.nugu_fab_blue_pressed
        drawableRes["fab_white"] = R.drawable.nugu_fab_white
        drawableRes["fab_white_pressed"] = R.drawable.nugu_fab_white_pressed
        drawableRes["fab_disabled"] = R.drawable.nugu_fab_disabled
        drawableRes["btn_blue_activated"] = R.drawable.nugu_btn_toggle_blue
        drawableRes["btn_white_activated"] = R.drawable.nugu_btn_toggle_white
        drawableRes["fab_blue_activated"] = R.drawable.nugu_btn_blue_activated
        drawableRes["fab_white_activated"] = R.drawable.nugu_btn_white_activated
        drawableRes["btn_white_micicon"] = R.drawable.nugu_btn_white_micicon
        drawableRes["btn_blue_micicon"] = R.drawable.nugu_btn_blue_micicon
        drawableRes["fab_blue_micicon"] = R.drawable.nugu_fab_blue_micicon
        drawableRes["fab_white_micicon"] = R.drawable.nugu_fab_white_micicon
        drawableRes["fab_blue_nugulogo"] = R.drawable.nugu_fab_blue_nugulogo
        drawableRes["fab_white_nugulogo"] = R.drawable.nugu_fab_white_nugulogo
        drawableRes["btn_blue_nugulogo"] = R.drawable.nugu_btn_blue_nugulogo
        drawableRes["btn_white_nugulogo"] = R.drawable.nugu_btn_white_nugulogo
        drawableRes["fab_disabled_micicon"] = R.drawable.nugu_fab_disabled_micicon
        drawableRes["btn_disabled_micicon"] = R.drawable.nugu_btn_disabled_micicon
    }

    /**
     * Set background color
     */
    @Throws(IllegalArgumentException::class)
    private fun setupBackground() {
        val type = when (buttonType) {
            TYPE_FAB -> {
                "fab"
            }
            TYPE_BUTTON -> {
                "btn"
            }
            else -> throw IllegalArgumentException("Illegal type value $buttonType")
        }
        val color = when (buttonColor) {
            COLOR_BLUE -> {
                "blue"
            }
            COLOR_WHITE -> {
                "white"
            }
            else -> throw IllegalArgumentException("Illegal type value $buttonColor")
        }

        val states = StateListDrawable()
        // states.setExitFadeDuration(android.R.integer.config_shortAnimTime)
        states.addState(
            intArrayOf(android.R.attr.state_activated),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + color + "_" + "activated"]
                    ?: throw IllegalArgumentException("resource not found: ${type}_${color}_activated")
            )
        )

        states.addState(
            intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_pressed),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + color]
                    ?: throw IllegalArgumentException("resource not found: ${type}_${color}")
            )
        )

        states.addState(
            intArrayOf(-android.R.attr.state_enabled),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + "disabled"]
                    ?: throw IllegalArgumentException("resource not found: ${type}_disabled")
            )
        )

        states.addState(
            intArrayOf(android.R.attr.state_pressed),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + color + "_" + "pressed"]
                    ?: throw IllegalArgumentException("resource not found: ${type}_${color}_pressed")
            )
        )
        background = states
    }

    @Throws(IllegalArgumentException::class)
    private fun setupImageDrawable() {
        //fab_white_
        val type = when (buttonType) {
            TYPE_FAB -> {
                "fab"
            }
            TYPE_BUTTON -> {
                "btn"
            }
            else -> throw IllegalArgumentException("Illegal type value $buttonType")
        }
        val color = when (buttonColor) {
            COLOR_BLUE -> {
                "blue"
            }
            COLOR_WHITE -> {
                "white"
            }
            else -> throw IllegalArgumentException("Illegal type value $buttonColor")
        }

        imageView.setImageResource(0)
        if (isEnabled) {
            if (!isActivated) {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        drawableRes["${type}_${color}_micicon"]
                            ?: throw IllegalArgumentException("resource not found: ${type}_${color}_micicon")
                    )
                )
            }
        } else {
            //fab_disabled_micicon
            imageView.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    drawableRes["${type}_disabled_micicon"]
                        ?: throw IllegalArgumentException("resource not found: ${type}_disabled_micicon")
                )
            )
        }
    }

    /**
     * This is called when the view is attached to a window.
     **/
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isEnabled && (autoPlay || wasAnimatingWhenDetached)) {
            playAnimation()
        }

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

        setupImageDrawable()

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

    @Throws(IllegalArgumentException::class)
    fun getImageDrawableResId(icon: Int): Drawable {
        val type = when (buttonType) {
            TYPE_FAB -> {
                "fab"
            }
            TYPE_BUTTON -> {
                "btn"
            }
            else -> throw IllegalArgumentException("Illegal type value $buttonType")
        }
        val color = when (buttonColor) {
            COLOR_BLUE -> {
                "blue"
            }
            COLOR_WHITE -> {
                "white"
            }
            else -> throw IllegalArgumentException("Illegal color value $buttonColor")
        }
        val icon = when (icon) {
            ICON_MIC -> {
                "micicon"
            }
            ICON_LOGO -> {
                "nugulogo"
            }
            else -> throw IllegalArgumentException("Illegal color value $icon")
        }
        return ContextCompat.getDrawable(
            context,
            drawableRes[type + "_" + color + "_" + icon]
                ?: throw IllegalArgumentException("resource not found: ${type}_${color}_${icon}")
        )!!
    }

    /**
     * Set animation
     */
    private fun setupAnimations() {
        imageView.setImageResource(0)
        imageView.setImageDrawable(getImageDrawableResId(ICON_MIC))

        val animator1 = ObjectAnimator.ofFloat(imageView, "rotationY", 0F, 90F)
        val animator2 = ObjectAnimator.ofFloat(imageView, "rotationY", -90F, 0F)
        val animator3 = ObjectAnimator.ofFloat(imageView, "rotationY", 0F, 90F)
        val animator4 = ObjectAnimator.ofFloat(imageView, "rotationY", -90F, 0F)

        animator1?.let {
            it.duration = 500
            it.startDelay = 0
        }
        animator2?.let {
            it.duration = 500
            it.startDelay = 0
            it.addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {
                    imageView.setImageResource(0)
                    imageView.setImageDrawable(getImageDrawableResId(ICON_LOGO))
                }

                override fun onAnimationEnd(animation: Animator?) {}
            })
        }
        animator3?.let {
            it.duration = 500
            it.startDelay = 3000
        }
        animator4?.let {
            it.duration = 500
            it.startDelay = 0
            it.addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {
                    imageView.setImageResource(0)
                    imageView.setImageDrawable(getImageDrawableResId(ICON_MIC))
                }

                override fun onAnimationEnd(animation: Animator?) {
                }
            })
        }
        animationSet = AnimatorSet().apply {
            play(animator1).before(animator2)
            play(animator2).before(animator3)
            play(animator3).before(animator4)

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {
                }

                override fun onAnimationEnd(animation: Animator?) {
                    if (loopPlay) {
                        animationSet?.startDelay = 3000
                        animationSet?.start()
                    }
                }

                override fun onAnimationCancel(animation: Animator?) {
                }

                override fun onAnimationStart(animation: Animator?) {
                }
            })
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val size = when (buttonType) {
            TYPE_FAB -> 72f
            else -> 56f
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(dpToPx(size, context), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dpToPx(size, context), MeasureSpec.EXACTLY))
    }

    /**
     * Start the animation playing
     */
    fun playAnimation() {
        if (running) {
            return
        }

        if (isShown) {
            if (animationSet == null) {
                setupAnimations()
            }
            animationSet?.start()

            running = true
        } else {
            wasAnimatingWhenNotShown = true
        }
    }

    /**
     * Resume the animation playing
     */
    fun resumeAnimation() {
        playAnimation()
    }

    /**
     * Pause the animation
     */
    fun pauseAnimation() {
        animationSet?.pause()
        running = false
    }

    /**
     * Stop(Cancels) the animation
     */
    fun stopAnimation() {
        running = false
        wasAnimatingWhenDetached = false
        wasAnimatingWhenNotShown = false
        animationSet?.removeAllListeners()
        animationSet?.end()
        animationSet?.cancel()
        animationSet = null
    }

    /**
     * Set the enabled state of this view.
     * @param enabled True if this view is enabled, false otherwise.
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            if (autoPlay) {
                stopAnimation()
            }
        }
        setupImageDrawable()
        invalidate()

        if (enabled) {
            if (autoPlay) {
                resumeAnimation()
            }
        }
    }

    /**
     * Set the button type.
     * @param color The new type to set in the button.
     */
    fun setButtonType(@ButtonTypes type: Int) {
        buttonType = type
        setupImageDrawable()
        setupBackground()
        requestLayout()
        invalidate()
    }


    /**
     * Set the button color.
     * @param color The new color to set in the button.
     */
    fun setButtonColor(@ButtonColors color: Int) {
        buttonColor = color
        setupImageDrawable()
        setupBackground()
        invalidate()
    }

}
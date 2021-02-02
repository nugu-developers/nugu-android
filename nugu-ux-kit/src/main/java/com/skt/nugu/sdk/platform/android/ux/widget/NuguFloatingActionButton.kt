/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.core.view.ViewCompat
import android.util.AttributeSet
import android.util.SparseArray
import android.view.AbsSavedState
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.skt.nugu.sdk.platform.android.ux.R

/**
 * A NUGU floating action button (FAB) is a circular button designed by Nugu Design guide.
 * @constructor Creates
 * @param context The Context
 * @param attrs Style attributes that differ from the default
 * @param defStyleAttr An attribute in the current theme that contains a
 *        reference to a style resource that supplies default values for
 *        the view. Can be 0 to not look for defaults.
 *
 * @attr ref R.styleable#NuguFloatingActionButton_fab_micStandardDrawable
 * @attr ref R.styleable#NuguFloatingActionButton_fab_nuguStandardDrawable
 * @attr ref R.styleable#NuguFloatingActionButton_fab_micLargeDrawable
 * @attr ref R.styleable#NuguFloatingActionButton_fab_nuguLargeDrawable
 * @attr ref R.styleable#NuguFloatingActionButton_fab_duration
 * @attr ref R.styleable#NuguFloatingActionButton_fab_color
 * @attr ref R.styleable#NuguFloatingActionButton_fab_borderColor
 * @attr ref R.styleable#NuguFloatingActionButton_fab_sizes
 */

@Deprecated("Use NuguButton")
class NuguFloatingActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    /** standard size */
    val SIZE_STANDARD = 0
    /** large size */
    val SIZE_LARGE = 1

    private var imageView: SparseArray<ImageView> = SparseArray()
    private var drawables: SparseArray<Drawable> = SparseArray()

    // attributes
    private var fabColor: Int = 0
    private var fabBorderColor: Int = 0
    private var fabSize: Int = 0
    private var duration: Long = 0
    private var autoPlay = false
    private var loopPlay = false

    private var wasAnimatingWhenDetached = false
    private var wasAnimatingWhenNotShown = false
    private var running = false
    private var isInitialized: Boolean = false


    private var animator : ObjectAnimator ?= null

    private val VIEW_MIC = 0
    private val VIEW_NUGU = 1
    private val VIEW_FLAGS = intArrayOf(VIEW_MIC, VIEW_NUGU)

    private val IMAGE_STANDARD_MIC = 0
    private val IMAGE_STANDARD_NUGU = 1
    private val IMAGE_LARGE_MIC = 2
    private val IMAGE_LARGE_NUGU = 3
    private val IMAGE_FLAGS = intArrayOf(IMAGE_STANDARD_MIC, IMAGE_STANDARD_NUGU, IMAGE_LARGE_MIC, IMAGE_LARGE_NUGU)


    private fun isAnimating(): Boolean = running

    init {
        init(attrs)
        setupView()
        setupBackground()
        sizeChange()
        setupAnimations()
        isInitialized = true
    }
    /** Gray color **/
    var GRAY = -0x877c71
    // initialize custom attributes
    private fun init(attrs: AttributeSet?) {
        context.obtainStyledAttributes(
            attrs, R.styleable.NuguFloatingActionButton, 0, 0
        ).apply {
            drawables.put(IMAGE_STANDARD_MIC, getDrawable(R.styleable.NuguFloatingActionButton_fab_micStandardDrawable))
            drawables.put(IMAGE_STANDARD_NUGU, getDrawable(R.styleable.NuguFloatingActionButton_fab_nuguStandardDrawable))
            drawables.put(IMAGE_LARGE_MIC, getDrawable(R.styleable.NuguFloatingActionButton_fab_micLargeDrawable))
            drawables.put(IMAGE_LARGE_NUGU, getDrawable(R.styleable.NuguFloatingActionButton_fab_nuguLargeDrawable))
            duration = getInteger(R.styleable.NuguFloatingActionButton_fab_duration, 1000).toLong()
            fabColor = getColor(R.styleable.NuguFloatingActionButton_fab_color, GRAY)
            fabBorderColor = getColor(R.styleable.NuguFloatingActionButton_fab_borderColor, 0x0C000000)
            fabSize = getInt(R.styleable.NuguFloatingActionButton_fab_sizes, SIZE_STANDARD)
            // for shadow
            ViewCompat.setElevation(this@NuguFloatingActionButton, getFloat(
                R.styleable.NuguFloatingActionButton_fab_elevation,
                resources.getDimensionPixelSize(R.dimen.fab_elevation).toFloat()
            ))
            loopPlay = getBoolean(R.styleable.NuguFloatingActionButton_fab_loop, true)
            autoPlay = getBoolean(R.styleable.NuguFloatingActionButton_fab_autoPlay, true)
        }.recycle()

    }

    /**
     * invoked when the activity may be temporarily destroyed, save the instance state here
     */
    override fun onSaveInstanceState(): Parcelable? {
        return SavedState(
            super.onSaveInstanceState() ?: Bundle.EMPTY,
            fabColor,
            fabBorderColor,
            fabSize,
            autoPlay,
            loopPlay,
            duration,
            running
        )
    }

    /**
     * This callback is called only when there is a saved instance that is previously saved by using
     * onSaveInstanceState(). We restore some state in onCreate(), while we can optionally restore
     * other state here, possibly usable after onStart() has completed.
     * The savedInstanceState Bundle is same as the one used in onCreate().
     */
    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)

        val ss = state as SavedState
        fabColor = ss.fabColor
        fabBorderColor = ss.fabBorderColor
        fabSize = ss.fabSize
        autoPlay = ss.autoPlay
        loopPlay = ss.loopPlay
        duration = ss.duration
        running = ss.running

        if (running ) {
            playAnimation()
        }
    }

    /**
     * Get state of pressed
     */
    fun getPressedState(pressedColor: Int): ColorStateList {
        return ColorStateList(arrayOf(intArrayOf()), intArrayOf(pressedColor))
    }

    /**
     * Set background color
     */
    private fun setupBackground() {
        //Creating gradient drawable from programmatically
        background = GradientDrawable().apply {
            // setStroke(10, fabBorderColor)
            shape = GradientDrawable.OVAL
            if (isEnabled) {
                setColor(fabColor)
            } else {
                setColor(GRAY)
            }
        }
    }

    /**
     * Called when a touch screen motion event occurs.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                background.setColorFilter(0x28000000, PorterDuff.Mode.SRC_ATOP)
            }
            MotionEvent.ACTION_UP -> {
                background.clearColorFilter()
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Initializes, Creates a view
     */
    private fun setupView() {
        removeAllViews()
        removeAllViewsInLayout()
        // Add Base Layout
        val baseLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
                .apply { gravity = Gravity.CENTER }
        }
        // Add MIC ImageView
        imageView.put(VIEW_MIC, ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        baseLayout.addView(imageView[VIEW_MIC])

        // Add NUGU ImageView
        imageView.put(VIEW_NUGU, ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        baseLayout.addView(imageView[VIEW_NUGU])

        addView(baseLayout)
    }

    /**
     * Set the visibility state of this view.
     * @param visibility is [View.VISIBLE], [View.INVISIBLE], [View.GONE]
     * @param index is VIEW_MIC, VIEW_NUGU
     */
    fun visibility(visibility: Int, index: Int = VIEW_NUGU) {
        imageView.get(index)?.visibility = visibility
        sizeChange()
    }

    /**
     * Set animation
     */
    private fun setupAnimations() {
        animator = ObjectAnimator.ofFloat(imageView.get(VIEW_MIC), "rotationY", 0F, 180f)
        animator?.let {
            it.duration = 1000
            it.startDelay = 0
            it.addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    if (loopPlay) {
                        animation?.startDelay = 5000
                        animation?.start()
                    }
                }
            })
        }
    }

    /**
     * Set the enabled state of this view.
     * @param enabled True if this view is enabled, false otherwise.
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        setupBackground()
        invalidate()

        if (enabled) {
            if(autoPlay) {
                resumeAnimation()
            }
        } else {
            stopAnimation()
        }
    }

    /**
     * Called when the visibility of the view or an ancestor of the view has changed.
     */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)

        if (!isInitialized) {
            return
        }

        if (visibility == View.VISIBLE) {
            if (wasAnimatingWhenNotShown) {
                resumeAnimation()
                wasAnimatingWhenNotShown = false
            }
        } else {
            if (isAnimating()) {
                pauseAnimation()
                wasAnimatingWhenNotShown = true
            }
        }
    }

    /**
     * This is called when the view is attached to a window.
     **/
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoPlay || wasAnimatingWhenDetached) {
            playAnimation()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // This is needed to mimic newer platform behavior.
            // https://stackoverflow.com/a/53625860/715633
            onVisibilityChanged(this, visibility)
        }
        sizeChange()
    }

    /**
     * This is called when the view is detached from a window.  At this point it
     * no longer has a surface for drawing.
     * @see [onAttachedToWindow]
     */
    override fun onDetachedFromWindow() {
        if (isAnimating()) {
            clearAnimation()
            wasAnimatingWhenDetached = true
        }
        super.onDetachedFromWindow()
    }

    /**
     * It should be called when the layout of the view changes.
     */
    private fun sizeChange() {
        when (fabSize) {
            SIZE_STANDARD -> {
                layoutParams?.apply {
                    this.width = resources.getDimensionPixelSize(R.dimen.fab_size_standard)
                    this.height = resources.getDimensionPixelSize(R.dimen.fab_size_standard)
                }

                imageView.get(VIEW_MIC)?.layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.fab_mic_size_standard),
                    resources.getDimensionPixelSize(R.dimen.fab_mic_size_standard)
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.fab_mic_bottom_margin)
                }
                imageView.get(VIEW_NUGU)?.layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.fab_nugu_width_standard),
                    resources.getDimensionPixelSize(R.dimen.fab_nugu_height_standard)
                ).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.fab_nugu_top_margin_standard)
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.fab_nugu_bottom_margin_standard)
                }

                imageView.get(VIEW_MIC).apply {
                    setImageResource(0)
                    setImageDrawable(drawables.get(IMAGE_STANDARD_MIC))
                }
                imageView.get(VIEW_NUGU).apply {
                    setImageResource(0)
                    setImageDrawable(drawables.get(IMAGE_STANDARD_NUGU))
                }
            }
            SIZE_LARGE -> {
                layoutParams?.apply {
                    this.width = resources.getDimensionPixelSize(R.dimen.fab_size_large)
                    this.height = resources.getDimensionPixelSize(R.dimen.fab_size_large)
                }

                imageView.get(VIEW_MIC)?.layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.fab_mic_size_large),
                    resources.getDimensionPixelSize(R.dimen.fab_mic_size_large)
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.fab_mic_bottom_margin)
                }

                imageView.get(VIEW_NUGU)?.layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.fab_mic_size_large),
                    resources.getDimensionPixelSize(R.dimen.fab_nugu_size_large)
                ).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.fab_nugu_top_margin_large)
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.fab_nugu_bottom_margin_large)
                }

                imageView.get(VIEW_MIC).apply {
                    setImageResource(0)
                    setImageDrawable(drawables.get(IMAGE_LARGE_MIC))
                }
                imageView.get(VIEW_NUGU).apply {
                    setImageResource(0)
                    setImageDrawable(drawables.get(IMAGE_LARGE_NUGU))
                }
            }
            else -> {
                // no op
            }
        }
    }

    /**
     * Start the animation playing
     */
    fun playAnimation() {
        if(running) {
            return
        }
        if (isShown && isEnabled) {
            if(animator == null) {
                setupAnimations()
            }
            animator?.startDelay = 0
            animator?.start()
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
        animator?.pause()
        running = false
    }

    /**
     * Stop(Cancels) the animation
     */
    fun stopAnimation() {
        running = false
        wasAnimatingWhenDetached = false
        wasAnimatingWhenNotShown = false
        animator?.removeAllListeners()
        animator?.end()
        animator?.cancel()
        animator = null
    }

    /**
     * Change the size
     * @param size SIZE_STANDARD, SIZE_LARGE
     * */
    fun setSize(size: Int) {
        if (fabSize == size) {
            return
        }
        fabSize = size
        sizeChange()

        visibility = View.GONE
        visibility = View.VISIBLE
        postDelayed( {
            requestLayout()
        }, 10)
    }

    /** SavedState implementation */
    internal class SavedState: AbsSavedState {
        var fabColor: Int = 0
        var fabBorderColor: Int = 0
        var fabSize: Int = 0
        var autoPlay: Boolean = false
        var loopPlay: Boolean = false
        var duration: Long = 0L
        var running: Boolean = false

        constructor(source: Parcel) : super(source) {
            fabColor = source.readInt()
            fabBorderColor = source.readInt()
            fabSize = source.readInt()
            autoPlay = source.readInt() != 0
            loopPlay = source.readInt() != 0
            duration = source.readLong()
            running = source.readInt() != 0
        }

        constructor(
            superState: Parcelable,
            fabColor: Int,
            fabBorderColor: Int,
            fabSize: Int,
            autoPlay: Boolean,
            loopPlay: Boolean,
            duration: Long,
            running:Boolean
        ) : super(superState) {
            this.fabColor = fabColor
            this.fabBorderColor = fabBorderColor
            this.fabSize = fabSize
            this.autoPlay = autoPlay
            this.loopPlay = loopPlay
            this.duration = duration
            this.running = running
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(fabColor)
            out.writeInt(fabBorderColor)
            out.writeInt(fabSize)
            out.writeInt(if (autoPlay) 1 else 0)
            out.writeInt(if (loopPlay) 1 else 0)
            out.writeLong(duration)
            out.writeInt(if (running) 1 else 0)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> =
                object : Parcelable.ClassLoaderCreator<SavedState> {
                    override fun createFromParcel(source: Parcel): SavedState {
                        return SavedState(source)
                    }

                    override fun createFromParcel(source: Parcel, loader: ClassLoader): SavedState {
                        return SavedState(source)
                    }

                    override fun newArray(size: Int): Array<SavedState?> {
                        return arrayOfNulls(size)
                    }
                }
        }
    }
}
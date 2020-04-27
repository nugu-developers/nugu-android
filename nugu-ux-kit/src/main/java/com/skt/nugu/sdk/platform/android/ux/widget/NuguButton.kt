package com.skt.nugu.sdk.platform.android.ux.widget

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Parcelable
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.skt.nugu.sdk.platform.android.ux.R

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

    val drawableRes: MutableMap<String, Int> = HashMap()

    init {
        init(attrs)
        loadDrawableRes()
        setupDrawable()
    }

    // initialize custom attributes
    private fun init(attrs: AttributeSet?) {
        context.obtainStyledAttributes(
            attrs, R.styleable.NuguButton, 0, 0
        ).apply {
            nuguButtonType = getInt(R.styleable.NuguButton_types, TYPE_FAB)
            nuguButtonColor = getInt(R.styleable.NuguButton_colors, COLOR_BLUE)
        }.recycle()

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
    }

    /**
     * Set background color
     */
    private fun setupDrawable() {
        val states = StateListDrawable()
        states.setExitFadeDuration(android.R.integer.config_shortAnimTime)

        val type = when(nuguButtonType) {
            TYPE_FAB -> {
                "fab"
            }
            TYPE_BUTTON -> {
                "btn"
            }
            else -> throw IllegalStateException(nuguButtonType.toString())
        }
        val color = when(nuguButtonColor) {
            COLOR_BLUE -> {
                "blue"
            }
            COLOR_WHITE -> {
                "white"
            }
            else -> throw IllegalArgumentException(nuguButtonColor.toString())
        }

        states.addState(
            intArrayOf(-android.R.attr.state_pressed),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + color] ?: throw IllegalArgumentException(type + "_" + color)
            )
        )
        states.addState(
            intArrayOf(android.R.attr.state_pressed),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + color + "_" +"pressed"]
                    ?: throw IllegalArgumentException(type + "_" + color + "_" +"pressed")
            )
        )
        states.addState(
            intArrayOf(android.R.attr.state_enabled),
            ContextCompat.getDrawable(
                context,
                drawableRes[type + "_" + color] ?: throw IllegalArgumentException(type + "_" + color)
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
}
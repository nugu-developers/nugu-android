package com.skt.nugu.sdk.platform.android.ux.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R

/**
 * This is View.
 * This is belonged to its parent View not Activity.
 * Unlike a standard Dialog in Android.
 */
class NuguSimpleDialogView(
    val context: Context,
    val root: ViewGroup,
    val title: String = "",
    private val positiveText: String? = null,
    private val negativeText: String? = null,
    private val onPositive: (() -> Unit)? = null,
    private val onNegative: (() -> Unit)? = null
) {
    fun show(): View {
        val dialog = LayoutInflater.from(context).inflate(R.layout.nugu_view_simple_dialog, null)

        dialog.findViewById<TextView>(R.id.nugu_simple_dialog_title).text = title

        positiveText?.run {
            dialog.findViewById<TextView>(R.id.nugu_simple_dialog_btn_positive).let { view ->
                view.visibility = View.VISIBLE
                view.text = this
                view.setThrottledOnClickListener {
                    root.removeView(dialog)
                    onPositive?.invoke()

                }
            }
        }

        negativeText?.run {
            dialog.findViewById<TextView>(R.id.nugu_simple_dialog_btn_negative).let { view ->
                view.visibility = View.VISIBLE
                view.text = this
                view.setThrottledOnClickListener {
                    root.removeView(dialog)
                    onNegative?.invoke()
                }
            }
        }

        dialog.setThrottledOnClickListener {
            root.removeView(dialog)
            onNegative?.invoke()
        }

        root.addView(dialog)
        return dialog
    }
}
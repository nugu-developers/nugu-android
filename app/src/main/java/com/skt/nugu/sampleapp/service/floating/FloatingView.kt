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
package com.skt.nugu.sampleapp.service.floating

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

class FloatingView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    def: Int = 0
) :
    FrameLayout(context, attributeSet, def), View.OnTouchListener {

    private var downRawX: Int = 0
    private var downRawY: Int = 0
    private var lastX: Int = 0
    private var lastY: Int = 0
    private lateinit var callbacks: Callbacks
    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        setOnTouchListener(this)
    }

    fun setCallbacks(callbacks: Callbacks) {
        this.callbacks = callbacks
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(motionEvent)

        val action = motionEvent.action
        val x = motionEvent.rawX.toInt()
        val y = motionEvent.rawY.toInt()

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = x
                downRawY = y
                lastX = x
                lastY = y
                return true // Consumed
            }
            MotionEvent.ACTION_MOVE -> {
                val nx = (x - lastX)
                val ny = (y - lastY)
                lastX = x
                lastY = y

                callbacks.onDrag(nx, ny)
                return true // Consumed
            }

            MotionEvent.ACTION_UP -> {
                return true
            }
            else -> {
                return super.onTouchEvent(motionEvent)
            }
        }
    }

    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            callbacks.onClick()
            return true
        }

        override fun onLongPress(e: MotionEvent?) {
            callbacks.onLongPress()
        }
    }

    interface Callbacks {
        fun onDrag(dx: Int, dy: Int){}
        fun onClick(){}
        fun onLongPress(){}
    }
}


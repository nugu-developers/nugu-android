/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.mockito.Mockito.mock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChromeWindowContentLayoutMock {
    var currentState : Int = BottomSheetBehavior.STATE_COLLAPSED
    val behavior = BehaviorMock()
    private val executor = Executors.newSingleThreadScheduledExecutor()
    inner class BehaviorMock {
        var state : Int = BottomSheetBehavior.STATE_COLLAPSED
        set(value) {
            field = BottomSheetBehavior.STATE_SETTLING
            bottomSheetCallback.onStateChanged(mock(View::class.java), BottomSheetBehavior.STATE_SETTLING)
            executor.schedule({
                field = value
                bottomSheetCallback.onStateChanged(mock(View::class.java), value)
            }, 10, TimeUnit.MILLISECONDS)
        }
    }

    private val lock = Object()
    internal fun notify() {
        synchronized(lock) {
            lock.notify()
        }
    }
    fun waitForState(state : Int, block: (Int) -> Unit) {
        while(this.currentState != state || behavior.state != state) {
            synchronized(lock) {
                lock.wait(1)
            }
        }
        block.invoke(state)
    }
    private val bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, @BottomSheetBehavior.State newState: Int) {
            val currentState = currentState
            // for test
            notify()

            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    setState(BottomSheetBehavior.STATE_HIDDEN)
                }
                BottomSheetBehavior.STATE_HIDDEN -> {
                }
                BottomSheetBehavior.STATE_DRAGGING ,
                BottomSheetBehavior.STATE_SETTLING -> {
                    return
                }
            }
            if( currentState != newState ) {
                behavior.state = currentState
            }
        }
        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    fun setState(newState: Int) : Boolean{
        if (currentState == newState) {
            return false
        }
        currentState = newState

        when (behavior.state) {
            BottomSheetBehavior.STATE_DRAGGING,
            BottomSheetBehavior.STATE_SETTLING-> {
                /** The state is changing, so wait in onStateChanged() for it to complete. **/
                return false
            }
            else -> {
                behavior.state = newState
            }
        }
        return true
    }

}
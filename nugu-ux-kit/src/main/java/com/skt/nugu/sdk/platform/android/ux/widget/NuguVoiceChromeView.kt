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
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A Voice Chrome View is a animated view designed by Nugu Design guide.
 * See https://github.com/airbnb/lottie-android
 */
class NuguVoiceChromeView : FrameLayout {
    /**
     * Provides animation information
     * @param resId is lottie resource
     * @param count is loop count of animation
     */
    inner class AnimationInfo(val resId: Int, val count: Int = 0)
    /**
     * Companion objects
     */
    companion object {
        private const val TAG = "VoiceChromeView"
    }

    private val queue = ConcurrentLinkedQueue<AnimationInfo>()

    private var lottieView: LottieAnimationView

    /**
     * VoiceChromeView constructor
     */
    constructor(context: Context) : this(context, null)

    /**
     * VoiceChromeView constructor
     */
    constructor(context: Context, attributeSet: AttributeSet?) : super(context, attributeSet) {
        lottieView = LottieAnimationView(context, attributeSet).also {
            it.setFailureListener {
                // TODO : fix me. https://github.com/nugu-developers/nugu-android/issues/2252
                Logger.e(TAG, "Failure", it)
            }
            it.enableMergePathsForKitKatAndAbove(true)
            it.addAnimatorListener(animationListener)
            addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            // Preload in cache
            it.setAnimation(R.raw.intro_01)
            it.setAnimation(R.raw.passive_02)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lottieView.removeAllAnimatorListeners()
    }

    private val isPendingAnimating = AtomicBoolean(false)
    private val animationListener = object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator?) {
            isPendingAnimating.set(false)
        }

        override fun onAnimationEnd(animation: Animator?) {
            nextAnimation()
        }

        override fun onAnimationCancel(animation: Animator?) {
            nextAnimation(true)
        }

        override fun onAnimationRepeat(animation: Animator?) {
            if(queue.size > 0) {
                animation?.cancel()
            }
        }
    }

    /**
     * Start animation sequentially
     * @param resId is lottie resource id
     * @param count is Repeat count
     */
    private fun addAnimation(resId: Int, count: Int = LottieDrawable.INFINITE) {
        queue.add(AnimationInfo(resId, count))
        nextAnimation()
    }

    /**
     * Start animation immediately
     * start Animation after clear animation
     * @param resId is lottie resource id
     * @param count is Repeat count
     */
    private fun setAnimation(resId: Int, count: Int = LottieDrawable.INFINITE) {
        queue.clear()
        queue.add(AnimationInfo(resId, count))

        isPendingAnimating.set(false)
        lottieView.cancelAnimation()
        nextAnimation()
    }

    private fun isAnimating() : Boolean{
        return lottieView.isAnimating || isPendingAnimating.get()
    }
    /**
     * Next Animation
     */
    private fun nextAnimation(cancel : Boolean = false) {
        if (isAnimating() && !cancel) {
            return
        }
        queue.poll()?.apply {
            isPendingAnimating.set(true)
            post {
                if (resId == 0) {
                    // for stopAnimation
                    lottieView.setImageDrawable(null)
                } else {
                    lottieView.setAnimation(resId)
                    lottieView.repeatCount = count
                    lottieView.playAnimation()
                }
            }
        }
    }

    /**
     * Start animation
     * @param animation @see [Animation]
     */
    fun startAnimation(animation: Animation) {
        when (animation) {
            Animation.WAITING -> {
                setAnimation(R.raw.intro_01, 1)
                addAnimation(R.raw.passive_02, LottieDrawable.INFINITE)
            }
            Animation.LISTENING -> {
                addAnimation(R.raw.transition_03, 1)
                addAnimation(R.raw.active_04, LottieDrawable.INFINITE)
            }
            Animation.SPEAKING_ERROR,
            Animation.SPEAKING -> {
                setAnimation(R.raw.transition_06, 1)
                addAnimation(R.raw.speaking_07, LottieDrawable.INFINITE)
            }
            Animation.THINKING -> {
                addAnimation(R.raw.processing_05, LottieDrawable.INFINITE)
            }
        }
    }

    /**
     * Cancel animation (immediately)
     */
    fun cancelAnimation() {
        queue.clear()

        isPendingAnimating.set(false)
        this.post {
            lottieView.cancelAnimation()
            lottieView.setImageDrawable(null)
        }
    }

    /**
     * Stop animation (It stop after animation ends)
     * Note : resid, 0 means draw transparent screen
     */
    fun stopAnimation() {
        this.addAnimation(0, LottieDrawable.INFINITE)
    }

    /**
     * The status of a voice animation
     * @see [https://developers-doc.nugu.co.kr/nugu-sdk/sdk-design-guide/voice-chrome#nugu-voice-chrome-1]
     */
    enum class Animation {
        /**
         * waiting(listening-passive) animation
         **/
        WAITING,
        /**
         * listening(listening-active)  animation
         **/
        LISTENING,
        /**
         * thinking(processing) animation
         **/
        THINKING,
        /**
         * speaking animation
         **/
        SPEAKING,
        /**
         * speaking error animation
         **/
        SPEAKING_ERROR
    }
}
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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.activity.main.MainActivity
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.platform.android.ux.widget.NuguVoiceChromeView

class FloatingHeadWindow(val context: Context) : FloatingView.Callbacks {

    private var windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var view: View
    private lateinit var sttView: TextView
    private lateinit var nuguBtn: View
    private lateinit var voiceChrome: NuguVoiceChromeView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isViewAdded = false

    private val dialogStateListener = object : DialogUXStateAggregatorInterface.Listener {
        override fun onDialogUXStateChanged(
            newState: DialogUXStateAggregatorInterface.DialogUXState,
            dialogMode: Boolean,
            chips: RenderDirective.Payload?,
            sessionActivated: Boolean
        ) {

            mainHandler.post {
                when (newState) {
                    DialogUXStateAggregatorInterface.DialogUXState.EXPECTING -> {
                        showViewAndHideOthers(voiceChrome)
                        voiceChrome.startAnimation(NuguVoiceChromeView.Animation.WAITING)
                    }
                    DialogUXStateAggregatorInterface.DialogUXState.LISTENING -> {
                        if (sttView.text.isNullOrBlank()) {
                            showViewAndHideOthers(voiceChrome)
                            voiceChrome.startAnimation(NuguVoiceChromeView.Animation.LISTENING)
                        }
                    }
                    DialogUXStateAggregatorInterface.DialogUXState.THINKING -> {
//                        showViewAndHideOthers(voiceChrome)
//                        voiceChrome.startAnimation(NuguVoiceChromeView.Animation.THINKING)
                    }
                    DialogUXStateAggregatorInterface.DialogUXState.SPEAKING -> {
                        showViewAndHideOthers(voiceChrome)
                        voiceChrome.startAnimation(NuguVoiceChromeView.Animation.SPEAKING)
                    }
                    DialogUXStateAggregatorInterface.DialogUXState.IDLE -> {
                        showViewAndHideOthers(nuguBtn)
                    }
                }
            }
        }
    }

    private val asrResultListener = object : ASRAgentInterface.OnResultListener {
        override fun onNoneResult(header: Header) {
        }

        override fun onPartialResult(result: String, header: Header) {
            mainHandler.post {
                showViewAndHideOthers(sttView)
                sttView.text = result
            }
        }

        override fun onCompleteResult(result: String, header: Header) {
            mainHandler.post {
                showViewAndHideOthers(sttView)
                sttView.text = result
            }
        }

        override fun onError(
            type: ASRAgentInterface.ErrorType,
            header: Header,
            allowEffectBeep: Boolean
        ) {
            mainHandler.post {
                sttView.visibility = View.GONE
                sttView.text = ""
            }
        }

        override fun onCancel(cause: ASRAgentInterface.CancelCause, header: Header) {
            mainHandler.post {
                sttView.visibility = View.GONE
                sttView.text = ""
            }
        }
    }

    fun show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(context)) {
                windowManager.addView(view, layoutParams)
            }
        } else {
            windowManager.addView(view, layoutParams)
        }
        isViewAdded = true

        ClientManager.getClient().addDialogUXStateListener(dialogStateListener)
        ClientManager.getClient().addASRResultListener(asrResultListener)
    }

    fun hide() {
        if (isViewAdded) {
            windowManager.removeView(view)
            isViewAdded = false

            ClientManager.getClient().removeDialogUXStateListener(dialogStateListener)
            ClientManager.getClient().removeASRResultListener(asrResultListener)
        }
    }

    fun create() {
        if (!isViewAdded) {
            view = LayoutInflater.from(context).inflate(R.layout.item_floaing, null, false)
            (view as FloatingView).setCallbacks(this)

            nuguBtn = view.findViewById(R.id.floating_nugu_button)
            voiceChrome = view.findViewById(R.id.floating_voice_chrome)
            sttView = view.findViewById(R.id.floating_stt)
        }
    }

    private fun showViewAndHideOthers(view: View) {
        view.visibility = View.VISIBLE
        val hideTargets = arrayListOf<View>()
        when (view) {
            nuguBtn -> {
                sttView.text = null
                hideTargets.add(sttView)
                hideTargets.add(voiceChrome)
            }
            voiceChrome -> {
                sttView.text = null
                hideTargets.add(sttView)
                hideTargets.add(nuguBtn)
            }
            sttView -> {
                hideTargets.add(voiceChrome)
                hideTargets.add(nuguBtn)
            }
        }

        hideTargets.forEach { it.visibility = View.INVISIBLE }
    }

    fun createLayoutParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT
            )
        } else {
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR),
                PixelFormat.TRANSLUCENT
            )
        }
        layoutParams.gravity = Gravity.CENTER
    }

    private fun moveBy(dx: Int, dy: Int) {
        if (isViewAdded) {

            layoutParams.x += dx
            layoutParams.y += dy

            val dm = context.resources.displayMetrics
            layoutParams.x = layoutParams.x.coerceAtLeast(-dm.widthPixels / 2 + getWidth() / 2)
                .coerceAtMost(dm.widthPixels / 2 - getWidth() / 2)
            layoutParams.y = layoutParams.y.coerceAtLeast(-dm.heightPixels / 2 + getHeight() / 2)
                .coerceAtMost(dm.heightPixels / 2 - getHeight() / 2)

            windowManager.updateViewLayout(view, layoutParams)
        }
    }

    private fun getWidth(): Int = view.measuredWidth

    private fun getHeight(): Int = view.measuredHeight

    override fun onDrag(dx: Int, dy: Int) {
        moveBy(dx, dy)
    }

    override fun onClick() {
        if (nuguBtn.visibility == View.VISIBLE) {
            ClientManager.getClient().asrAgent?.startRecognition(initiator = ASRAgentInterface.Initiator.TAP)
        } else if (ClientManager.speechRecognizerAggregator.isActive()) {
            ClientManager.speechRecognizerAggregator.stopListening()
        } else {
            ClientManager.getClient().localStopTTS()
        }
    }

    override fun onLongPress() {
        startActivity()
    }

    private fun startActivity() {
        try {
            val contentIntent = PendingIntent.getActivity(
                context,
                9999,
                Intent(context, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                                or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    ),
                //.putExtras(pushIntent),
                PendingIntent.FLAG_ONE_SHOT
            )

            contentIntent.send()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

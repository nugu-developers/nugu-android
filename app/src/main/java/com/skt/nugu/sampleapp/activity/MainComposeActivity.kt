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
package com.skt.nugu.sampleapp.activity

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import com.skt.nugu.sdk.platform.android.ux.widget.ChromeWindow
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton

/**
 * This is very simple example to apply NUGU UI component to Activity which is based on Compose View.
 * This sample not include permission and network state check and interaction among other NUGU UI components.
 * Take a look 'MainActivity' for checking state handling and interaction.
 */
class MainComposeActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainComposeActivity"
    }

    private lateinit var chromeWindow: ChromeWindow

    private val templateRenderer: TemplateRenderer by lazy {
        TemplateRenderer(object : TemplateRenderer.NuguClientProvider {
            override fun getNuguClient(): NuguAndroidClient = ClientManager.getClient()
        }, ConfigurationStore.configuration.deviceTypeCode, null, R.id.template_container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
              
                AndroidView(
                    factory = { context ->
                        FrameLayout(context).apply {

                            // template container
                            addView(FrameLayout(context).apply {
                                id = R.id.template_container
                            })

                            // chrome window
                            addView(FrameLayout(context).apply {
                                chromeWindow = ChromeWindow(context, this, object : ChromeWindow.NuguClientProvider {
                                    override fun getNuguClient() = ClientManager.getClient()
                                })
                            })
                        }

                    }, modifier = Modifier.fillMaxSize()
                )

                // nugu button
                AndroidView(factory = { context ->
                    NuguButton(context).apply {
                        setOnClickListener {
                            ClientManager.speechRecognizerAggregator.startListening(initiator = ASRAgentInterface.Initiator.TAP)
                        }
                    }
                }, modifier = Modifier.align(Alignment.BottomEnd))
            }
        }

        ClientManager.getClient().setDisplayRenderer(templateRenderer.also {
            it.setFragmentManager(supportFragmentManager)

            ConfigurationStore.templateServerUri { url, error ->
                error?.apply {
                    Log.e(TAG, "[onCreate] error=$this")
                    return@templateServerUri
                }
                it.setServerUrl(url)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        ClientManager.speechRecognizerAggregator.startListeningWithTrigger()
    }

    override fun onPause() {
        super.onPause()
        ClientManager.speechRecognizerAggregator.stop()
    }

    override fun onBackPressed() {
        if (chromeWindow.isShown()) {
            ClientManager.speechRecognizerAggregator.stopListening()
            return
        }

        if (templateRenderer.clearAll()) return

        super.onBackPressed()
    }
}
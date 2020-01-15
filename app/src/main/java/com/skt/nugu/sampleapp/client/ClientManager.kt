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
package com.skt.nugu.sampleapp.client

import android.content.Context
import android.util.Log
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.audiosource.AudioSourceManager
import com.skt.nugu.sdk.platform.android.audiosource.audiorecord.AudioRecordSourceFactory
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechProcessorDelegate
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregator
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sdk.external.jademarble.EndPointDetector
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * This class manage everything related to NUGU.
 * * manage resources of keyword detector.
 * * manage audio source for speech recognizer
 * * provide client for NUGU
 */
object ClientManager : AudioPlayerAgentInterface.Listener {
    private const val TAG = "ClientMananger"

    private lateinit var client: NuguAndroidClient

    lateinit var ariaResource: SpeechRecognizerAggregator.KeywordResources
    lateinit var tinkerbellResource: SpeechRecognizerAggregator.KeywordResources

    private val audioSourceManager = AudioSourceManager(AudioRecordSourceFactory())
    lateinit var speechRecognizerAggregator: SpeechRecognizerAggregator

    var playerActivity: AudioPlayerAgentInterface.State = AudioPlayerAgentInterface.State.IDLE

    private val executor = Executors.newSingleThreadExecutor()

    var initialized = false
    var observer: Observer? = null
        set(value) {
            field = value
            if (initialized) {
                value?.onInitialized()
            }
        }

    interface Observer {
        fun onInitialized()
    }

    private fun loadAssets(context: Context) {
        val dataDirectory = context.getDir("skt_nugu_assets", Context.MODE_PRIVATE)
        val dataFiles = dataDirectory.listFiles()
        val assets = context.assets.list("") ?: return

        for (asset in assets) {
            val needCopy = if (asset.startsWith("skt_")) {
                var retVal = false
                for (dataFile in dataFiles) {
                    if (dataFile.name == asset) {
                        retVal = true
                        break
                    }
                }
                retVal
            } else {
                true
            }

            if (!needCopy) {
                context.assets.open(asset).use { inputStream ->
                    var read = 0
                    val buffer = ByteArray(2048)
                    FileOutputStream(dataDirectory.absolutePath + File.separator + asset).use { outputStream ->
                        while ({ read = inputStream.read(buffer, 0, buffer.size); read }() > 0) {
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        if (initialized) {
            return
        }

        executor.submit {
            loadAssets(context.applicationContext)
            init(context.applicationContext, object :
                KeywordResourceProviderInterface {
                private val assetsFolder =
                    context.getDir("skt_nugu_assets", Context.MODE_PRIVATE).absolutePath
                private val aria = SpeechRecognizerAggregator.KeywordResources(
                    assetsFolder + File.separator + "skt_trigger_am_aria.raw",
                    assetsFolder + File.separator + "skt_trigger_search_aria.raw"
                )
                private val tinkerbell = SpeechRecognizerAggregator.KeywordResources(
                    assetsFolder + File.separator + "skt_trigger_am_tinkerbell.raw",
                    assetsFolder + File.separator + "skt_trigger_search_tinkerbell.raw"
                )

                override fun provideDefault(): SpeechRecognizerAggregator.KeywordResources = aria

                override fun provideAria(): SpeechRecognizerAggregator.KeywordResources = aria

                override fun provideTinkerbell(): SpeechRecognizerAggregator.KeywordResources =
                    tinkerbell
            })
        }
    }

    private fun init(context: Context, keywordResourceProvider: KeywordResourceProviderInterface) {
        ariaResource = keywordResourceProvider.provideAria()
        tinkerbellResource = keywordResourceProvider.provideTinkerbell()

        // Create NuguAndroidClient
        client = NuguAndroidClient.Builder(
            context,
            NuguOAuth.create(context),
            audioSourceManager
        ).defaultEpdTimeoutMillis(7000L)
            .endPointDetector(
            EndPointDetector(
                context.getDir(
                    "skt_nugu_assets",
                    Context.MODE_PRIVATE
                ).absolutePath + File.separator + "skt_epd_model.raw"
            )
        ).build()

        client.addAudioPlayerListener(this)

        val asrAgent = client.asrAgent
        if(asrAgent == null) {
            Log.e(TAG, "asrAgent is null.")
            throw RuntimeException("asrAgent cannot be null")
        }

        speechRecognizerAggregator = SpeechRecognizerAggregator(
            keywordResourceProvider.provideAria(),
            SpeechProcessorDelegate(asrAgent),
            audioSourceManager
        )

        val wakeupWordStateProvider = object : ContextStateProvider {
            override val namespaceAndName: NamespaceAndName =
                NamespaceAndName("client", "wakeupWord")

            override fun provideState(
                contextSetter: ContextSetterInterface,
                namespaceAndName: NamespaceAndName,
                stateRequestToken: Int
            ) {
                val wakeupWord = if (PreferenceHelper.triggerId(context) == 0) {
                    "아리아"
                } else {
                    "팅커벨"
                }
                contextSetter.setState(
                    namespaceAndName,
                    wakeupWord,
                    StateRefreshPolicy.ALWAYS,
                    stateRequestToken
                )
            }
        }

        client.setStateProvider(wakeupWordStateProvider.namespaceAndName, wakeupWordStateProvider)

        initialized = true
        observer?.onInitialized()
    }

    fun getClient() = client

    override fun onStateChanged(
        activity: AudioPlayerAgentInterface.State,
        context: AudioPlayerAgentInterface.Context
    ) {
        playerActivity = activity
    }
}
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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.player.SamplePlayerFactory
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.routine.RoutineAgent
import com.skt.nugu.sdk.agent.sound.SoundProvider
import com.skt.nugu.sdk.client.SdkContainer
import com.skt.nugu.sdk.client.agent.factory.AgentFactory
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.client.configuration.configure
import com.skt.nugu.sdk.client.port.transport.grpc2.GrpcTransportFactory
import com.skt.nugu.sdk.client.port.transport.grpc2.NuguServerInfo
import com.skt.nugu.sdk.core.interfaces.context.WakeupWordContextProvider
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.external.jademarble.EndPointDetector
import com.skt.nugu.sdk.external.keensense.KeensenseKeywordDetector
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.audiosource.AudioSourceManager
import com.skt.nugu.sdk.platform.android.audiosource.audiorecord.AudioRecordSourceFactory
import com.skt.nugu.sdk.platform.android.beep.AsrBeepResourceProvider
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechProcessorDelegate
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregator
import com.skt.nugu.sdk.platform.android.speechrecognizer.measure.SimplePcmPowerMeasure
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors

/**
 * This class manage everything related to NUGU.
 * * manage resources of keyword detector.
 * * manage audio source for speech recognizer
 * * provide client for NUGU
 */
object ClientManager : AudioPlayerAgentInterface.Listener {
    private const val TAG = "ClientManager"

    private lateinit var client: NuguAndroidClient

    lateinit var ariaResource: KeensenseKeywordDetector.KeywordResources
    lateinit var tinkerbellResource: KeensenseKeywordDetector.KeywordResources

    private val audioSourceManager = AudioSourceManager(AudioRecordSourceFactory())
    var keywordDetector: KeensenseKeywordDetector? = null
    lateinit var speechRecognizerAggregator: SpeechRecognizerAggregator

    var playerActivity: AudioPlayerAgentInterface.State = AudioPlayerAgentInterface.State.IDLE

    private val executor = Executors.newSingleThreadExecutor()

    private val directiveHandlingListener = object: DirectiveSequencerInterface.OnDirectiveHandlingListener {
        private val TAG = "DirectiveEvent"
        override fun onRequested(directive: Directive) {
            Log.d(TAG, "[onRequested] ${directive.header}")
        }

        override fun onCompleted(directive: Directive) {
            Log.d(TAG, "[onCompleted] ${directive.header}")
        }

        override fun onCanceled(directive: Directive) {
            Log.d(TAG, "[onCanceled] ${directive.header}")
        }

        override fun onFailed(directive: Directive, description: String) {
            Log.d(TAG, "[onFailed] ${directive.header} , $description")
        }
    }

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

        ConfigurationStore.configure(context = context, filename = "nugu-config.json" /* nugu-config.json is the default and can be omitted */)

        executor.submit {
            loadAssets(context.applicationContext)
            init(context.applicationContext, object :
                KeywordResourceProviderInterface {
                private val assetsFolder =
                    context.getDir("skt_nugu_assets", Context.MODE_PRIVATE).absolutePath
                private val aria = KeensenseKeywordDetector.KeywordResources(
                    "아리아",
                    assetsFolder + File.separator + "skt_trigger_am_aria.raw",
                    assetsFolder + File.separator + "skt_trigger_search_aria.raw"
                )
                private val tinkerbell = KeensenseKeywordDetector.KeywordResources(
                    "팅커벨",
                    assetsFolder + File.separator + "skt_trigger_am_tinkerbell.raw",
                    assetsFolder + File.separator + "skt_trigger_search_tinkerbell.raw"
                )

                override fun provideDefault(): KeensenseKeywordDetector.KeywordResources = aria

                override fun provideAria(): KeensenseKeywordDetector.KeywordResources = aria

                override fun provideTinkerbell(): KeensenseKeywordDetector.KeywordResources =
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
            .addAgentFactory(RoutineAgent.NAMESPACE, object : AgentFactory<RoutineAgent> {
                override fun create(container: SdkContainer): RoutineAgent = with(container) {
                    RoutineAgent(
                        getMessageSender(),
                        getContextManager(),
                        getDirectiveSequencer(),
                        getDirectiveSequencer(),
                        getDirectiveGroupProcessor(),
                        getAudioSeamlessFocusManager(),
                        getPlaySynchronizer()
                    )
                }
            })
            .transportFactory(
                GrpcTransportFactory(NuguServerInfo(object : NuguServerInfo.Delegate {
                    override fun getNuguServerInfo() : NuguServerInfo {
                        val metadata = ConfigurationStore.configurationMetadataSync()
                        return NuguServerInfo.Builder().deviceGW(metadata?.deviceGatewayServerGrpcUri)
                            .registry(metadata?.deviceGatewayRegistryUri)
                            .keepConnection(NuguOAuth.getClient().isSidSupported())
                            .build()
                    }
                }))
            )
            .endPointDetector(
                EndPointDetector(
                    context.getDir(
                        "skt_nugu_assets",
                        Context.MODE_PRIVATE
                    ).absolutePath + File.separator + "skt_epd_model.raw"
                )
            ).soundProvider(object : SoundProvider {
                override fun getContentUri(name: SoundProvider.BeepName): URI {
                    return URI.create(
                        Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + R.raw.responsefail_800ms)
                            .toString()
                    );
                }
            })
            .asrBeepResourceProvider(object : AsrBeepResourceProvider {
                private val wakeupResource = URI.create(
                    Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + R.raw.wakeup_500ms)
                        .toString()
                )

                private val responseFailResource = URI.create(
                    Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + R.raw.responsefail_800ms)
                        .toString()
                )

                private val responseSuccessResource = URI.create(
                    Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + R.raw.responsesuccess_800ms)
                        .toString()
                )
                override fun getOnStartListeningResource(): URI? {
                    return if (PreferenceHelper.enableWakeupBeep(context)) {
                        wakeupResource
                    } else {
                        null
                    }
                }

                override fun getOnErrorNetworkResource(): URI? {
                    return if (PreferenceHelper.enableRecognitionBeep(context)) {
                        responseFailResource
                    } else {
                        null
                    }
                }

                override fun getOnErrorAudioInputResource(): URI? {
                    return if (PreferenceHelper.enableRecognitionBeep(context)) {
                        responseFailResource
                    } else {
                        null
                    }
                }

                override fun getOnErrorListeningTimeoutResource(): URI? {
                    return if (PreferenceHelper.enableRecognitionBeep(context)) {
                        responseFailResource
                    } else {
                        null
                    }
                }

                override fun getOnErrorUnknownResource(): URI? = null

                override fun getOnErrorResponseTimeoutResource(): URI? = null

                override fun getOnNoneResultResource(): URI? {
                    return if (PreferenceHelper.enableRecognitionBeep(context)) {
                        responseFailResource
                    } else {
                        null
                    }
                }

                override fun getOnCompleteResultResource(): URI? {
                    return if (PreferenceHelper.enableRecognitionBeep(context)) {
                        responseSuccessResource
                    } else {
                        null
                    }
                }
            }).playerFactory(SamplePlayerFactory(context, useExoPlayer = true)).build()

        client.addAudioPlayerListener(this)
        client.addOnDirectiveHandlingListener(directiveHandlingListener)

        val asrAgent = client.asrAgent
        if(asrAgent == null) {
            Log.e(TAG, "asrAgent is null.")
            throw RuntimeException("asrAgent cannot be null")
        }

        val keensenseKeywordDetector = KeensenseKeywordDetector(keywordResourceProvider.provideAria(), SimplePcmPowerMeasure())
        keywordDetector = keensenseKeywordDetector
        speechRecognizerAggregator = SpeechRecognizerAggregator(
            keensenseKeywordDetector,
            SpeechProcessorDelegate(asrAgent),
            audioSourceManager,
            Handler(Looper.getMainLooper())
        )

        val wakeupWordStateProvider = object : WakeupWordContextProvider() {
            override fun getWakeupWord(): String = if (PreferenceHelper.triggerId(context) == 0) {
                "아리아"
            } else {
                "팅커벨"
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
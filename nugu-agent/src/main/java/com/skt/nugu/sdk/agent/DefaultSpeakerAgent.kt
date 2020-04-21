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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.agent.speaker.Speaker
import com.skt.nugu.sdk.agent.speaker.SpeakerManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.agent.speaker.SpeakerManagerObserver
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import java.util.concurrent.Executors

class DefaultSpeakerAgent(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender
) : AbstractCapabilityAgent(NAMESPACE), SpeakerManagerInterface {
    companion object {
        private const val TAG = "SpeakerManager"

        const val NAMESPACE = "Speaker"
        private val VERSION = Version(1,0)

        private const val NAME_SET_VOLUME = "SetVolume"
        private const val NAME_SET_MUTE = "SetMute"

        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val SET_VOLUME = NamespaceAndName(
            NAMESPACE,
            NAME_SET_VOLUME
        )

        private val SET_MUTE = NamespaceAndName(
            NAMESPACE,
            NAME_SET_MUTE
        )

        const val KEY_PLAY_SERVICE_ID = "playServiceId"
    }

    internal data class SetVolumePayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("rate")
        val rate: Speaker.Rate?,
        @SerializedName("volumes")
        val volumes: Array<Volume>
    )

    internal data class SetMutePayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("volumes")
        val volumes: Array<Volume>
    )

    internal data class Volume(
        @SerializedName("name")
        val name: Speaker.Type,
        @SerializedName("volume")
        val volume: Long?,
        @SerializedName("mute")
        val mute: Boolean?
    )

    private val settingObservers: MutableSet<SpeakerManagerObserver> = HashSet()
    private val speakerMap: MutableMap<Speaker.Type, Speaker> = HashMap()

    private val executor = Executors.newSingleThreadExecutor()

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    private fun executeSetVolume(
        type: Speaker.Type,
        volume: Int,
        rate: Speaker.Rate,
        source: SpeakerManagerObserver.Source,
        forceNoNotifications: Boolean = false
    ): Boolean {
        Logger.d(TAG, "[executeSetVolume] $type , $volume , $source, $forceNoNotifications")
        val speakers = speakerMap.filter { it.key == type }.values

        for (speaker in speakers) {
            if (!speaker.setVolume(volume, rate)) {
                return false
            }
        }

        if (forceNoNotifications) {
            executeNotifySettingsChanged(speakers, source)
        }

        return true
    }

    private fun executeSetMute(
        type: Speaker.Type,
        mute: Boolean,
        source: SpeakerManagerObserver.Source,
        forceNoNotifications: Boolean = false
    ): Boolean {
        Logger.d(TAG, "[executeSetMute] $type , $mute , $source, $forceNoNotifications")
        val speakers = speakerMap.filter { it.key == type }.values

        for (speaker in speakers) {
            if (!speaker.setMute(mute)) {
                return false
            }
        }

        if (!forceNoNotifications) {
            executeNotifySettingsChanged(speakers, source)
        }

        return true
    }

    private fun executeNotifySettingsChanged(
        speakers: Collection<Speaker>,
        source: SpeakerManagerObserver.Source
    ) {
        for (observer in settingObservers) {
            observer.onSpeakerSettingsChanged(source, speakers)
        }
    }

    override fun addSpeakerManagerObserver(observer: SpeakerManagerObserver) {
        executor.submit {
            settingObservers.add(observer)
        }
    }

    override fun removeSpeakerManagerObserver(observer: SpeakerManagerObserver) {
        executor.submit {
            settingObservers.remove(observer)
        }
    }

    override fun addSpeaker(speaker: Speaker) {
        speakerMap[speaker.getSpeakerType()] = speaker
    }

    override fun getSpeakerSettings(type: Speaker.Type) =
        speakerMap[type]?.getSpeakerSettings()

    override fun preHandleDirective(info: DirectiveInfo) {
        // No-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        when (info.directive.getNamespaceAndName()) {
            SET_VOLUME -> handleSetVolume(info)
            SET_MUTE -> handleSetMute(info)
        }
    }

    private fun handleSetVolume(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, SetVolumePayload::class.java)
        if(payload == null) {
            Logger.d(TAG, "[handleSetVolume] invalid payload")
            setHandlingFailed(info, "[handleSetVolume] invalid payload")
            return
        }

        if (payload.playServiceId.isBlank()) {
            Logger.d(TAG, "[handleExecute] missing field: playServiceId")
            setHandlingFailed(info, "[handleExecute] missing field: playServiceId")
            return
        }

        executor.submit {
            var success = true
            payload.volumes.forEach {
                val volume = it.volume
                if(volume != null) {
                    if(!executeSetVolume(it.name, it.volume.toInt(), payload.rate ?: Speaker.Rate.FAST, SpeakerManagerObserver.Source.DIRECTIVE)) {
                        success = false
                    }
                }
            }

            val referrerDialogRequestId = info.directive.header.dialogRequestId
            if (success) {
                sendSpeakerEvent("${NAME_SET_VOLUME}${NAME_SUCCEEDED}", payload.playServiceId, referrerDialogRequestId)
            } else {
                sendSpeakerEvent("${NAME_SET_VOLUME}${NAME_FAILED}", payload.playServiceId, referrerDialogRequestId)
            }

            executeSetHandlingCompleted(info)
        }
    }

    private fun handleSetMute(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, SetMutePayload::class.java)
        if(payload == null) {
            Logger.d(TAG, "[handleSetMute] invalid payload")
            setHandlingFailed(info, "[handleSetMute] invalid payload")
            return
        }

        if (payload.playServiceId.isBlank()) {
            Logger.d(TAG, "[handleSetMute] missing field: playServiceId")
            setHandlingFailed(info, "[handleSetMute] missing field: playServiceId")
            return
        }

        executor.submit {
            var success = true
            payload.volumes.forEach {
                val mute = it.mute
                if(mute != null) {
                    if(!executeSetMute(it.name, it.mute,SpeakerManagerObserver.Source.DIRECTIVE)) {
                        success = false
                    }
                }
            }

            val referrerDialogRequestId = info.directive.header.dialogRequestId
            if (success) {
                sendSpeakerEvent("${NAME_SET_MUTE}${NAME_SUCCEEDED}", payload.playServiceId, referrerDialogRequestId)
            } else {
                sendSpeakerEvent("${NAME_SET_MUTE}${NAME_FAILED}", payload.playServiceId, referrerDialogRequestId)
            }
            executeSetHandlingCompleted(info)
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info)
    }

    private fun executeSetHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info)
    }

    private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
        info.result.setFailed(msg)
        removeDirective(info)
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            true
        )

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[SET_VOLUME] = nonBlockingPolicy
        configuration[SET_MUTE] = nonBlockingPolicy

        return configuration
    }

    private fun removeDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    data class SpeakerContext(
        val minVolume: Int,
        val maxVolume: Int,
        val defaultVolumeStep: Int,
        var settings: Speaker.SpeakerSettings?
    )

    private val lastUpdatedSpeaker = HashMap<Speaker.Type, SpeakerContext>()
    private var isFirstContextUpdate = true

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            Logger.d(TAG, "[provideState]")
            val changed: Boolean = if(isFirstContextUpdate) {
                isFirstContextUpdate = false

                speakerMap.forEach {
                    lastUpdatedSpeaker[it.key] = SpeakerContext(
                        it.value.getMinVolume(),
                        it.value.getMaxVolume(),
                        it.value.getDefaultVolumeStep(),
                        it.value.getSpeakerSettings()
                    )
                }
                true
            } else {
                if (speakerMap.keys == lastUpdatedSpeaker.keys) {
                    var changed = false
                    speakerMap.forEach { speaker ->
                        speaker.value.getSpeakerSettings().let { settings ->
                            lastUpdatedSpeaker[speaker.key].let { updatedSpeaker ->
                                if (settings != updatedSpeaker?.settings) {
                                    changed = true
                                    updatedSpeaker?.settings = settings
                                }
                            }
                        }
                    }
                    changed
                } else {
                    lastUpdatedSpeaker.clear()
                    speakerMap.forEach {
                        lastUpdatedSpeaker[it.key] = SpeakerContext(
                            it.value.getMinVolume(),
                            it.value.getMaxVolume(),
                            it.value.getDefaultVolumeStep(),
                            it.value.getSpeakerSettings()
                        )
                    }
                    true
                }
            }

            if(changed) {
                contextSetter.setState(namespaceAndName, JsonObject().apply {
                    addProperty("version", VERSION.toString())
                    add("volumes", JsonArray().apply {
                        lastUpdatedSpeaker.forEach {
                            add(JsonObject().apply {
                                addProperty("name", it.key.name)
                                addProperty("minVolume", it.value.minVolume)
                                addProperty("maxVolume", it.value.maxVolume)
                                addProperty("defaultVolumeStep", it.value.defaultVolumeStep)

                                it.value.settings?.apply {
                                    addProperty("volume", volume)
                                    addProperty("muted", mute)
                                }
                            })
                        }
                    })
                }.toString(), StateRefreshPolicy.ALWAYS, stateRequestToken)
            } else {
                contextSetter.setState(namespaceAndName, null, StateRefreshPolicy.ALWAYS, stateRequestToken)
            }
        }
    }

    private fun sendSpeakerEvent(eventName: String, playServiceId: String, referrerDialogRequestId: String) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                val request =
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, eventName, VERSION.toString())
                        .payload(JsonObject().apply {
                            addProperty(KEY_PLAY_SERVICE_ID, playServiceId)
                        }.toString())
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
                messageSender.sendMessage(request)
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        })
    }
}
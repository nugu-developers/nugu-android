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
package com.skt.nugu.sdk.core.capabilityagents.impl

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.core.interfaces.capability.speaker.AbstractSpeakerAgent
import com.skt.nugu.sdk.core.interfaces.capability.speaker.SpeakerAgentFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.capability.speaker.Speaker
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.capability.speaker.SpeakerManagerObserver
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

object DefaultSpeakerAgent {
    private const val TAG = "SpeakerManager"

    val FACTORY = object : SpeakerAgentFactory {
        override fun create(
            contextManager: ContextManagerInterface,
            messageSender: MessageSender
        ): AbstractSpeakerAgent = Impl(
            contextManager,
            messageSender
        )
    }

    internal class Impl(
        contextManager: ContextManagerInterface,
        messageSender: MessageSender
    ) : AbstractSpeakerAgent(contextManager, messageSender) {
        companion object {
            const val NAME_SET_VOLUME = "SetVolume"
            const val NAME_SET_VOLUME_SUCCEEDED = "SetVolumeSucceeded"
            const val NAME_SET_VOLUME_FAILED = "SetVolumeFailed"
            const val NAME_SET_MUTE = "SetMute"
            const val NAME_SET_MUTE_SUCCEEDED = "SetMuteSucceeded"
            const val NAME_SET_MUTE_FAILED = "SetMuteFailed"

            val SET_VOLUME = NamespaceAndName(
                NAMESPACE,
                NAME_SET_VOLUME
            )
            val SET_MUTE = NamespaceAndName(
                NAMESPACE,
                NAME_SET_MUTE
            )

            const val KEY_PLAY_SERVICE_ID = "playServiceId"
            const val KEY_NAME = "name"
            const val KEY_VOLUME = "volume"
            const val KEY_MUTE = "mute"
            const val KEY_STATUS = "status"
        }

        private val settingObservers: MutableSet<SpeakerManagerObserver> = HashSet()
        private val speakerMap: MutableMap<Speaker.Type, Speaker> = HashMap()

        private val executor = Executors.newSingleThreadExecutor()

        override val namespaceAndName: NamespaceAndName =
            NamespaceAndName("supportedInterfaces", NAMESPACE)

        init {
            contextManager.setStateProvider(namespaceAndName, this)
        }

        override fun setVolume(
            type: Speaker.Type,
            volume: Int,
            forceNoNotifications: Boolean
        ): Future<Boolean> {
            Logger.d(TAG, "[setVolume] $type $volume $forceNoNotifications")
            return executor.submit(Callable<Boolean> {
                executeSetVolume(
                    type,
                    volume,
                    SpeakerManagerObserver.Source.LOCAL_API,
                    forceNoNotifications
                )
            })
        }

        override fun setMute(
            type: Speaker.Type,
            mute: Boolean,
            forceNoNotifications: Boolean
        ): Future<Boolean> {
            Logger.d(TAG, "[setMute] $type $mute $forceNoNotifications")
            return executor.submit(Callable<Boolean> {
                executeSetMute(
                    type,
                    mute,
                    SpeakerManagerObserver.Source.LOCAL_API,
                    forceNoNotifications
                )
            })
        }

        private fun executeSetVolume(
            type: Speaker.Type,
            volume: Int,
            source: SpeakerManagerObserver.Source,
            forceNoNotifications: Boolean = false
        ): Boolean {
            Logger.d(TAG, "[executeSetVolume] $type , $volume , $source, $forceNoNotifications")
            val speakers = speakerMap.filter { it.key == type }.values

            for (speaker in speakers) {
                if (!speaker.setVolume(volume)) {
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
            // TODO : XXX
            setHandlingFailed(info, "[handleSetVolume] not implemented yet")
            /*
            val directive = info.directive
            val volume = directive.retrieveValueAsLong(KEY_VOLUME)

            val playServiceId = info.directive.retrieveValueAsString(KEY_PLAY_SERVICE_ID)
            if (playServiceId.isNullOrBlank()) {
                Logger.d(TAG, "[handleExecute] missing field: playServiceId")
                setHandlingFailed(info, "[handleExecute] missing field: playServiceId")
                return
            }

            if (volume == null) {
                Logger.w(TAG, "[handleSetVolume] volume field is null")
                setHandlingFailed(info, "[handleSetVolume] volume field is null")
                return
            }

            val name = directive.retrieveValueAsString(KEY_NAME)
            if (name.isNullOrBlank()) {
                Logger.w(TAG, "[handleSetVolume] name field is null")
                setHandlingFailed(info, "[handleSetVolume] name field is null")
                return
            }

            val type = try {
                Speaker.Type.valueOf(name)
            } catch (th: Throwable) {
                null
            }

            if (type == null) {
                Logger.w(TAG, "[handleSetVolume] invalid name: $name")
                setHandlingFailed(info, "[handleSetVolume] invalid name: $name")
                return
            }


            executor.submit {
                if (executeSetVolume(
                        type,
                        volume.toInt(),
                        SpeakerManagerObserver.Source.DIRECTIVE
                    )
                ) {
                    sendSetVolumeSucceededEvent(playServiceId, name)
                } else {
                    sendSetVolumeFailedEvent(playServiceId, name)
                }
                executeSetHandlingCompleted(info)
            }
             */
        }

        private fun handleSetMute(info: DirectiveInfo) {
            // TODO : XXX
            setHandlingFailed(info, "[handleSetMute] not implemented yet")
            /*
            val directive = info.directive
            val mute = directive.retrieveValueAsBoolean(KEY_MUTE)

            val playServiceId = info.directive.retrieveValueAsString(KEY_PLAY_SERVICE_ID)
            if (playServiceId.isNullOrBlank()) {
                Logger.d(TAG, "[handleExecute] missing field: playServiceId")
                setHandlingFailed(info, "[handleExecute] missing field: playServiceId")
                return
            }

            if (mute == null) {
                Logger.d(TAG, "[handleSetMute] mute field is null")
                return
            }

            val name = directive.retrieveValueAsString(KEY_NAME)
            if (name.isNullOrBlank()) {
                Logger.w(TAG, "[handleSetMute] name field is null")
                setHandlingFailed(info, "[handleSetMute] name field is null")
                return
            }

            val type = try {
                Speaker.Type.valueOf(name)
            } catch (th: Throwable) {
                null
            }

            if (type == null) {
                Logger.w(TAG, "[handleSetMute] invalid name: $name")
                setHandlingFailed(info, "[handleSetMute] invalid name: $name")
                return
            }

            executor.submit {
                if (executeSetMute(
                        type,
                        mute,
                        SpeakerManagerObserver.Source.DIRECTIVE
                    )
                ) {
                    sendSetMuteSucceededEvent(playServiceId, name)
                } else {
                    sendSetMuteFailedEvent(playServiceId, name)
                }
                executeSetHandlingCompleted(info)
            }
             */
        }

        override fun cancelDirective(info: DirectiveInfo) {
            removeDirective(info)
        }

        private fun executeSetHandlingCompleted(info: DirectiveInfo) {
            info.result?.setCompleted()
            removeDirective(info)
        }

        private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
            info.result?.setFailed(msg)
            removeDirective(info)
        }

        override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
            val nonBlockingPolicy = BlockingPolicy()

            val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

            configuration[SET_VOLUME] = nonBlockingPolicy
            configuration[SET_MUTE] = nonBlockingPolicy

            return configuration
        }

        private fun removeDirective(info: DirectiveInfo) {
            removeDirective(info.directive.getMessageId())
        }

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            stateRequestToken: Int
        ) {
            Logger.d(TAG, "[provideState]")
            contextSetter.setState(namespaceAndName, JsonObject().apply {
                addProperty("version", VERSION)
                add("volumes", JsonArray().apply {
                    speakerMap.forEach {
                        add(JsonObject().apply {
                            // name으로 변경 필요
                            addProperty("name", it.key.name)
                            addProperty("minVolume", it.value.getMinVolume())
                            addProperty("maxVolume", it.value.getMaxVolume())

                            val settings = it.value.getSpeakerSettings()
                            settings?.apply {
                                addProperty("volume", volume)
                                addProperty("muted", mute)
                            }
                        })
                    }
                })
            }.toString(), StateRefreshPolicy.ALWAYS, stateRequestToken)
        }

        private fun sendSetVolumeSucceededEvent(playServiceId: String, name: String) {
            sendSpeakerEvent(NAME_SET_VOLUME_SUCCEEDED, playServiceId, name)
        }

        private fun sendSetVolumeFailedEvent(playServiceId: String, name: String) {
            sendSpeakerEvent(NAME_SET_VOLUME_FAILED, playServiceId, name)
        }

        private fun sendSetMuteSucceededEvent(playServiceId: String, name: String) {
            sendSpeakerEvent(NAME_SET_MUTE_SUCCEEDED, playServiceId, name)
        }

        private fun sendSetMuteFailedEvent(playServiceId: String, name: String) {
            sendSpeakerEvent(NAME_SET_MUTE_FAILED, playServiceId, name)
        }

        private fun sendSpeakerEvent(eventName: String, playServiceId: String, name: String) {
            contextManager.getContext(object : ContextRequester {
                override fun onContextAvailable(jsonContext: String) {
                    val request =
                        EventMessageRequest.Builder(jsonContext, NAMESPACE, eventName, VERSION)
                            .payload(JsonObject().apply {
                                addProperty(KEY_PLAY_SERVICE_ID, playServiceId)
                                addProperty(KEY_NAME, name)
                            }.toString()).build()
                    messageSender.sendMessage(request)
                }

                override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                }
            })
        }
    }
}
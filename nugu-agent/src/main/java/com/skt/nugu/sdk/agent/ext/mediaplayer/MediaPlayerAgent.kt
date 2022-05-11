/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.ext.mediaplayer

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.ext.mediaplayer.event.*
import com.skt.nugu.sdk.agent.ext.mediaplayer.handler.*
import com.skt.nugu.sdk.agent.ext.mediaplayer.payload.*
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class MediaPlayerAgent(
    private val mediaPlayer: MediaPlayer,
    messageSender: MessageSender,
    contextGetter: ContextGetterInterface,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    directiveSequencer: DirectiveSequencerInterface
) : CapabilityAgent
    , SupportedInterfaceContextProvider
    , PlayDirectiveHandler.Controller
    , StopDirectiveHandler.Controller
    , SearchDirectiveHandler.Controller
    , PreviousDirectiveHandler.Controller
    , NextDirectiveHandler.Controller
    , MoveDirectiveHandler.Controller
    , ResumeDirectiveHandler.Controller
    , PauseDirectiveHandler.Controller
    , RewindDirectiveHandler.Controller
    , ToggleDirectiveHandler.Controller
    , GetInfoDirectiveHandler.Controller
    , HandlePlaylistDirectiveHandler.Controller
    , HandleLyricsDirectiveHandler.Controller
{
    companion object {
        private const val TAG = "MediaPlayerAgent"

        const val NAMESPACE = "MediaPlayer"
        val VERSION = Version(1, 2)
    }

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this)
        directiveSequencer.apply {
            addDirectiveHandler(
                PlayDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )
            addDirectiveHandler(
                StopDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )
            addDirectiveHandler(
                SearchDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )

            addDirectiveHandler(
                PreviousDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )

            addDirectiveHandler(
                NextDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )

            addDirectiveHandler(
                MoveDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )

            addDirectiveHandler(
                ResumeDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )

            addDirectiveHandler(
                PauseDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )

            addDirectiveHandler(
                RewindDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )

            addDirectiveHandler(
                ToggleDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )

            addDirectiveHandler(
                HandlePlaylistDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )

            addDirectiveHandler(
                HandleLyricsDirectiveHandler(
                    this@MediaPlayerAgent,
                    messageSender,
                    contextGetter
                )
            )
        }
    }

    private val executor = Executors.newSingleThreadExecutor()

    internal data class StateContext(val context: Context): BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            with(context) {
                addProperty("appStatus", appStatus)
                addProperty("playerActivity", playerActivity.name)
                user?.let {
                    add("user", it.toJson())
                }
                currentSong?.let {
                    add("currentSong", it.toJson())
                }
                playlist?.let {
                    add("playlist", it.toJson())
                }
                toggle?.let {
                    add("toggle", it.toJson())
                }
            }
        }.toString()
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(
            TAG,
            "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken"
        )
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                if (contextType == ContextType.COMPACT) StateContext.CompactContextState else StateContext(
                    mediaPlayer.getContext()
                ),
                StateRefreshPolicy.ALWAYS,
                contextType,
                stateRequestToken
            )
        }
    }

    override fun play(header: Header, payload: PlayPayload, callback: PlayCallback) {
        executor.submit {
            mediaPlayer.play(header, payload, callback)
        }
    }

    override fun search(header: Header, payload: SearchPayload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.search(header, payload, callback)
        }
    }

    override fun previous(header: Header, payload: PreviousPayload, callback: PreviousCallback) {
        executor.submit {
            mediaPlayer.previous(header, payload, callback)
        }
    }

    override fun next(header: Header, payload: NextPayload, callback: NextCallback) {
        executor.submit {
            mediaPlayer.next(header, payload, callback)
        }
    }

    override fun move(header: Header, payload: MovePayload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.move(header, payload, callback)
        }
    }

    override fun resume(header: Header, payload: Payload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.resume(header, payload, callback)
        }
    }

    override fun stop(header: Header, payload: Payload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.stop(header, payload, callback)
        }
    }

    override fun pause(header: Header, payload: Payload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.pause(header, payload, callback)
        }
    }

    override fun rewind(header: Header, payload: Payload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.rewind(header, payload, callback)
        }
    }

    override fun toggle(header: Header, payload: TogglePayload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.toggle(header, payload, callback)
        }
    }

    override fun getInfo(header: Header, payload: Payload, callback: GetInfoCallback) {
        executor.submit {
            mediaPlayer.getInfo(header, payload, callback)
        }
    }

    override fun handlePlaylist(
        header: Header,
        payload: HandlePlaylistPayload,
        callback: EventCallback
    ) {
        executor.submit {
            mediaPlayer.handlePlaylist(header, payload, callback)
        }
    }

    override fun handleLyrics(
        header: Header,
        payload: HandleLyricsPayload,
        callback: EventCallback
    ) {
        executor.submit {
            mediaPlayer.handleLyrics(header, payload, callback)
        }
    }
}
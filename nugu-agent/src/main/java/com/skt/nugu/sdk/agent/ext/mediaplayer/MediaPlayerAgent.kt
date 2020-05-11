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

import com.skt.nugu.sdk.agent.ext.mediaplayer.handler.*
import com.skt.nugu.sdk.agent.ext.mediaplayer.payload.*
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.SupportedInterfaceContextProvider
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.util.concurrent.Executors

class MediaPlayerAgent(
    private val mediaPlayer: MediaPlayer,
    messageSender: MessageSender,
    contextGetter: ContextGetterInterface,
    directiveSequencer: DirectiveSequencerInterface
) : CapabilityAgent
    , SupportedInterfaceContextProvider
    , PlayDirectiveHandler.Controller
    , SearchDirectiveHandler.Controller
    , PreviousDirectiveHandler.Controller
    , NextDirectiveHandler.Controller
    , MoveDirectiveHandler.Controller
    , ResumeDirectiveHandler.Controller
    , PauseDirectiveHandler.Controller
    , RewindDirectiveHandler.Controller
    , ToggleDirectiveHandler.Controller
{
    companion object {
        private const val TAG = "MediaPlayerAgent"

        const val NAMESPACE = "MediaPlayer"
        val VERSION = Version(1, 0)
    }

    init {
        directiveSequencer.apply {
            addDirectiveHandler(
                PlayDirectiveHandler(
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
        }
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun getInterfaceName(): String = NAMESPACE

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
    }

    override fun play(payload: PlayPayload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.play(payload, callback)
        }
    }

    override fun search(payload: PlayPayload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.search(payload, callback)
        }
    }

    override fun previous(payload: PreviousPayload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.previous(payload, callback)
        }
    }

    override fun next(payload: PreviousPayload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.next(payload, callback)
        }
    }

    override fun move(payload: MovePayload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.move(payload, callback)
        }
    }

    override fun resume(payload: Payload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.resume(payload, callback)
        }
    }

    override fun pause(payload: Payload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.pause(payload, callback)
        }
    }

    override fun rewind(payload: Payload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.rewind(payload, callback)
        }
    }

    override fun toggle(payload: TogglePayload, callback: EventCallback) {
        executor.submit {
            mediaPlayer.toggle(payload, callback)
        }
    }
}
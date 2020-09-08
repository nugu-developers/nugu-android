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
package com.skt.nugu.sdk.agent.playback.impl

import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.agent.playback.PlaybackHandler
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.Executors

class PlaybackController(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender
) : ContextRequester
    , PlaybackHandler {
    companion object {
        private const val TAG = "PlaybackController"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val commands = LinkedList<PlaybackCommand>()

    override fun onContextAvailable(jsonContext: String) {
        Logger.d(TAG, "[onContextAvailable] $jsonContext")
        executor.submit {
            Logger.d(TAG, "[onContextAvailable] 0")
            if (commands.isEmpty()) {
                Logger.w(TAG, "[onContextAvailableExecutor] Queue is empty, return.")
                return@submit
            }

            Logger.d(TAG, "[onContextAvailable] 1")
            messageSender.newCall(buildPlaybackMessageRequest(commands.pop())).enqueue(object : MessageSender.Callback{
                override fun onFailure(request: MessageRequest, status: Status) {
                }

                override fun onSuccess(request: MessageRequest) {
                }

                override fun onResponseStart(request: MessageRequest) {
                }
            })

            if (commands.isNotEmpty()) {
                Logger.d(TAG, "[onContextAvailableExecutor] Queue is not empty, call getContext().")
                contextManager.getContext(this)
            }
        }
    }

    override fun onContextFailure(
        error: ContextRequester.ContextRequestError,
        jsonContext: String
    ) {
        Logger.d(TAG, "[onContextFailure]")

        executor.submit {
            if (commands.isEmpty()) {
                Logger.w(TAG, "[onContextFailureExecutor] Queue is empty, return.")
                return@submit
            }

            val command = commands.pop()
            Logger.d(TAG, "[onContextFailureExecutor] ButtonPressed : $command / error : $error")

            if (commands.isNotEmpty()) {
                contextManager.getContext(this)
            }
        }
    }

    private fun buildPlaybackMessageRequest(command: PlaybackCommand): PlaybackMessageRequest {
        // TODO : impl
        return PlaybackMessageRequest()
    }

    override fun onButtonPressed(button: PlaybackButton) {
        handleCommand(
            PlaybackCommand.buttonToCommand(
                button
            )
        )
    }

//    override fun onTogglePressed(toggle: PlaybackToggle, action: Boolean) {
//        handleCommand(PlaybackCommand.toggleToCommand(toggle, action))
//    }

    private fun handleCommand(command: PlaybackCommand) {
        Logger.d(TAG, "[handleCommand] command : $command")
        executor.submit {
            Logger.d(TAG, "[handleCommandExecutor] command : $command")

            if (commands.isEmpty()) {
                Logger.d(TAG, "[handleCommandExecutor] Queue is empty, call getContext()")
                contextManager.getContext(this)
            }

            commands.offer(command)
        }
    }
}
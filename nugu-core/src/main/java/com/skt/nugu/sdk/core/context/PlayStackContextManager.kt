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
package com.skt.nugu.sdk.core.context

import com.google.gson.JsonArray
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import java.util.concurrent.Executors

/**
 * Provides integrated playstack of audio and visual.
 * @param contextManager : the context manager
 * @param audioPlayStackProvider : the audio playstack provider
 * @param visualPlayStackProvider : the visual playstack provider
 */
class PlayStackContextManager(
    contextManager: ContextManagerInterface,
    private val audioPlayStackProvider: PlayStackProvider,
    private val visualPlayStackProvider: PlayStackProvider?
) : ContextStateProvider {
    companion object {
        private const val TAG = "PlayStackContextManager"

        private const val CONTEXT_NAMESPACE = "client"
        private const val PROVIDER_NAME = "playStack"
    }

    override val namespaceAndName: NamespaceAndName = NamespaceAndName(CONTEXT_NAMESPACE, PROVIDER_NAME)
    private val executor = Executors.newSingleThreadExecutor()

    init {
        contextManager.setStateProvider(namespaceAndName,this)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                buildContext(),
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }
    }

    /**
     * build integrated playstack
     *
     * the visual is higher priority than audio.
     */
    private fun buildPlayStack() = LinkedHashSet<String>().apply {
        visualPlayStackProvider?.getPlayStack()?.let { stack ->
            while (stack.isNotEmpty()) {
                stack.pop().let {
                    if(it.isNotBlank()) {
                        add(it)
                    }
                }
            }
        }

        audioPlayStackProvider.getPlayStack().let { stack ->
            while (stack.isNotEmpty()) {
                stack.pop().let {
                    if(it.isNotBlank()) {
                        add(it)
                    }
                }
            }
        }
    }

    private fun buildContext(): String {
        return JsonArray().apply {
            buildPlayStack().forEach {
                add(it)
            }
        }.toString()
    }
}
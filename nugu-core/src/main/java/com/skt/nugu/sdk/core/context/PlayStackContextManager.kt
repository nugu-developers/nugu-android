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
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet

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
) : ClientContextProvider {
    companion object {
        private const val TAG = "PlayStackContextManager"

        private const val PROVIDER_NAME = "playStack"
    }

    override fun getName(): String = PROVIDER_NAME

    private val executor = Executors.newSingleThreadExecutor()

    internal data class StateContext(private val playStack: List<String>): ContextState {
        override fun toFullJsonString(): String = JsonArray().apply {
            playStack.forEach {
                add(it)
            }
        }.toString()
        override fun toCompactJsonString(): String = toFullJsonString()
    }

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                StateContext(buildPlayStack()),
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
    private fun buildPlayStack(): List<String> {
        // use treemap to order (ascending)
        val playStackMap = TreeMap<Long, String>()

        visualPlayStackProvider?.getPlayStack()?.apply {
            forEach {
                playStackMap[it.timestamp] = it.playServiceId
            }
        }

        audioPlayStackProvider.getPlayStack().apply {
            forEach {
                playStackMap[it.timestamp] = it.playServiceId
            }
        }

        // remove duplicated value
        val playStackSet = LinkedHashSet<String>().apply {
            playStackMap.descendingMap().forEach {
                add(it.value)
            }
        }

        // convert to list
        val playStackList = ArrayList<String>().apply {
            playStackSet.forEach {
                add(it)
            }
        }

        Logger.d(TAG, "[buildPlayStack] playStack: $playStackList, map: $playStackMap")

        return playStackList
    }
}
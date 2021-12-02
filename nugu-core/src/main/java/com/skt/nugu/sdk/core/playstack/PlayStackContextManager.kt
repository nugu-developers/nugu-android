/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.core.playstack

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
    private val visualPlayStackProvider: PlayStackProvider? = null
) : ClientContextProvider {
    companion object {
        private const val TAG = "PlayStackContextManager"

        internal const val PROVIDER_NAME = "playStack"
    }

    override val namespaceAndName: NamespaceAndName = NamespaceAndName(ClientContextProvider.NAMESPACE, PROVIDER_NAME)

    private val executor = Executors.newSingleThreadExecutor()

    private val timeStampDescendingComparator =
        Comparator<PlayStackProvider.PlayStackContext> { p0, p1 ->
            val diff = p1.timestamp - p0.timestamp
            when {
                diff > 0 -> {
                    1
                }
                diff < 0 -> {
                    -1
                }
                else -> {
                    0
                }
            }
        }

    internal data class StateContext(private val playStack: List<String>): ClientContextState {
        override fun value(): String  = JsonArray().apply {
            playStack.forEach {
                add(it)
            }
        }.toString()
    }

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                StateContext(buildPlayStack()),
                StateRefreshPolicy.ALWAYS,
                contextType,
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
        // use treemap to order (descending)
        val foregroundPlayStackMap = TreeMap<PlayStackProvider.PlayStackContext, String>(timeStampDescendingComparator)
        val backgroundPlayStackMap = TreeMap<PlayStackProvider.PlayStackContext, String>(timeStampDescendingComparator)

        visualPlayStackProvider?.getPlayStack()?.let { list ->
            var foundBackgroundPlayStack = false

            list.forEach {
                if (it.isBackground && !foundBackgroundPlayStack) {
                    foundBackgroundPlayStack = true
                }

                if (foundBackgroundPlayStack) {
                    backgroundPlayStackMap[it] = it.playServiceId
                } else {
                    foregroundPlayStackMap[it] = it.playServiceId
                }
            }
        }

        audioPlayStackProvider.getPlayStack().let { list ->
            var foundBackgroundPlayStack = false

            list.forEach {
                if (it.isBackground && !foundBackgroundPlayStack) {
                    foundBackgroundPlayStack = true
                }

                if (foundBackgroundPlayStack) {
                    backgroundPlayStackMap[it] = it.playServiceId
                } else {
                    foregroundPlayStackMap[it] = it.playServiceId
                }
            }
        }

        // remove duplicated value
        val playStackSet = LinkedHashSet<String>().apply {
            foregroundPlayStackMap.forEach {
                add(it.value)
            }
            backgroundPlayStackMap.forEach {
                add(it.value)
            }
        }

        // convert to list
        val playStackList = ArrayList<String>().apply {
            playStackSet.forEach {
                add(it)
            }
        }

        Logger.d(TAG, "[buildPlayStack] playStack: $playStackList, foregroundPlayStackMap: $foregroundPlayStackMap, backgroundPlayStackMap: $backgroundPlayStackMap")

        return playStackList
    }
}
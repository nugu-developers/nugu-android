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

import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.LoopThread
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.withLock

class ContextManager : ContextManagerInterface {
    companion object {
        private const val TAG = "ContextManager"
    }

    private data class GetContextParam(
        val contextRequester: ContextRequester,
        val target: NamespaceAndName?,
        val given: HashMap<NamespaceAndName, ContextState>?
    )

    private data class StateInfo(
        var stateProvider: ContextStateProvider?,
        var refreshPolicy: StateRefreshPolicy = StateRefreshPolicy.ALWAYS,
        var stateContext: CachedStateContext? = null
    )

    private data class CachedStateContext(val delegate: ContextState) : ContextState by delegate {
        val fullStateJsonString = toFullJsonString()
        val compactStateJsonString = toCompactJsonString()
    }

    private val namespaceNameToStateInfo: MutableMap<NamespaceAndName, StateInfo> = HashMap()
    private val namespaceToNameStateInfo: MutableMap<String, MutableMap<String, StateInfo>> = HashMap()

    private val contextRequesterQueue: Queue<GetContextParam> =
        LinkedList()
    private val pendingOnStateProviders = HashSet<NamespaceAndName>()
    private val stateProviderLock = ReentrantLock()
    private var stateRequestToken: Int = 0

    private val stringBuilderForContext = StringBuilder(8192)

    private val updateStatesThread: LoopThread = object : LoopThread() {
        private val PROVIDE_STATE_DEFAULT_TIMEOUT = 2000L

        override fun onLoop() {
            stateProviderLock.withLock {
                requestStatesLocked()
            }

            if (waitUntilRequestStatesFinished()) {
                // Finished
                sendContextToRequesters()
            } else {
                // Timeout
                sendContextToRequesters(ContextRequester.ContextRequestError.STATE_PROVIDER_TIMEOUT)
            }
        }

        private fun waitUntilRequestStatesFinished(): Boolean {
            val start = System.currentTimeMillis()
            while (pendingOnStateProviders.isNotEmpty()) {
                if (System.currentTimeMillis() - start > PROVIDE_STATE_DEFAULT_TIMEOUT) {
                    Logger.w(
                        TAG,
                        "[waitUntilRequestStateFinished] failed pending : ${pendingOnStateProviders.size}"
                    )
                    stateProviderLock.withLock {
                        pendingOnStateProviders.forEach {
                            Logger.w(TAG, "[waitUntilRequestStateFinished] failed pending : $it")
                        }
                    }
                    return false
                }
                sleep(10)
            }
            return true
        }
    }

    init {
        updateStatesThread.start()
    }

    private fun requestStatesLocked() {
        Logger.d(TAG, "[requestStatesLocked]")
        stateRequestToken++

        if (0 == stateRequestToken) {
            stateRequestToken++
        }

        val curStateReqToken = stateRequestToken

        for (it in namespaceNameToStateInfo) {
            val stateInfo = it.value
            if (StateRefreshPolicy.ALWAYS == stateInfo.refreshPolicy ||
                StateRefreshPolicy.SOMETIMES == stateInfo.refreshPolicy
            ) {
                pendingOnStateProviders.add(it.key)
                stateInfo.stateProvider?.provideState(this, it.key, curStateReqToken)
            }
        }
    }

    private fun sendContextToRequesters(error: ContextRequester.ContextRequestError? = null) {
        Logger.d(TAG, "[sendContextToRequesters]")

        synchronized(contextRequesterQueue) {
            while (contextRequesterQueue.isNotEmpty()) {
                with(contextRequesterQueue.poll()) {
                    val strContext: String = stateProviderLock.withLock {
                        buildContext(target, given)
                    }
                    if(error == null) {
                        contextRequester.onContextAvailable(strContext)
                    } else {
                        contextRequester.onContextFailure(error, strContext)
                    }
                }
            }
        }
    }

    private fun buildContext(target: NamespaceAndName?, given: Map<NamespaceAndName, ContextState>? = null): String = stringBuilderForContext.apply {
        // clear
        setLength(0)
        // write
        var keyIndex = 0
        append('{')

        namespaceToNameStateInfo.forEach { namespaceEntry ->
            if (keyIndex > 0) {
                append(',')
            }
            keyIndex++

            append("\"${namespaceEntry.key}\":")
            append('{')
            var valueIndex = 0
            namespaceEntry.value.forEach {
                val fullState = it.value.stateContext?.fullStateJsonString
                val compactState = it.value.stateContext?.compactStateJsonString
                if (fullState.isNullOrEmpty() && it.value.refreshPolicy == StateRefreshPolicy.SOMETIMES) {
                    // pass
                } else {
                    if (valueIndex > 0) {
                        append(',')
                    }
                    valueIndex++

                    append("\"${it.key}\":")
                    val givenContextState = given?.get(NamespaceAndName(namespaceEntry.key, it.key))
                    if (target == null || (target.namespace == namespaceEntry.key && target.name == it.key) || compactState == null) {
                        // need full
                        // full case
                        if(givenContextState == null) {
                            append(fullState)
                        } else {
                            append(givenContextState.toFullJsonString())
                        }
                    } else {
                        if(givenContextState == null) {
                            append(compactState)
                        } else {
                            append(givenContextState.toCompactJsonString())
                        }
                    }
                }
            }
            append('}')
        }
        append('}')
    }.toString()

    override fun setStateProvider(
        namespaceAndName: NamespaceAndName,
        stateProvider: ContextStateProvider?
    ) {
        stateProviderLock.withLock {
            if (stateProvider == null) {
                namespaceNameToStateInfo.remove(namespaceAndName)
                namespaceToNameStateInfo[namespaceAndName.namespace]?.remove(namespaceAndName.name)
            } else {
                val stateInfo = namespaceNameToStateInfo[namespaceAndName]
                if (stateInfo == null) {
                    createNewStateInfo(namespaceAndName, StateInfo(stateProvider))
                } else {
                    stateInfo.stateProvider = stateProvider
                }
            }
        }
    }

    private fun createNewStateInfo(namespaceAndName: NamespaceAndName, stateInfo: StateInfo) {
        namespaceNameToStateInfo[namespaceAndName] = stateInfo
        var map = namespaceToNameStateInfo[namespaceAndName.namespace]
        if(map == null) {
            map = HashMap()
            namespaceToNameStateInfo[namespaceAndName.namespace] = map
        }
        map[namespaceAndName.name] = stateInfo
    }

    override fun setState(
        namespaceAndName: NamespaceAndName,
        state: ContextState,
        refreshPolicy: StateRefreshPolicy,
        stateRequestToken: Int
    ): ContextSetterInterface.SetStateResult {
        stateProviderLock.withLock {
            Logger.d(
                TAG,
                "[setState] namespaceAndName: $namespaceAndName, state: $state, policy: $refreshPolicy, $stateRequestToken"
            )

            if (0 == stateRequestToken) {
                return updateStateLocked(namespaceAndName, state, refreshPolicy)
            }

            if (stateRequestToken != this.stateRequestToken) {
                return ContextSetterInterface.SetStateResult.STATE_TOKEN_OUTDATED
            }

            val status = updateStateLocked(namespaceAndName, state, refreshPolicy)
            if (ContextSetterInterface.SetStateResult.SUCCESS == status) {
                pendingOnStateProviders.remove(namespaceAndName)

                if (pendingOnStateProviders.isEmpty()) {
                    // notify provider
                }
            }

            return status
        }
    }

    private fun updateStateLocked(
        namespaceAndName: NamespaceAndName,
        state: ContextState,
        refreshPolicy: StateRefreshPolicy
    ): ContextSetterInterface.SetStateResult {
        Logger.d(TAG, "[updateStateLocked] $namespaceAndName, $state, $refreshPolicy")
        val stateInfo = namespaceNameToStateInfo[namespaceAndName]

        return if (stateInfo == null) {
            return when (refreshPolicy) {
                StateRefreshPolicy.ALWAYS, StateRefreshPolicy.SOMETIMES -> {
                    ContextSetterInterface.SetStateResult.STATE_PROVIDER_NOT_REGISTERED
                }
                StateRefreshPolicy.NEVER -> {
                    createNewStateInfo(namespaceAndName, StateInfo(null, refreshPolicy, CachedStateContext(state)))
                    ContextSetterInterface.SetStateResult.SUCCESS
                }
            }
        } else {
            if(stateInfo.stateContext?.delegate != state) {
                Logger.d(TAG, "[updateStateLocked] update current: ${stateInfo.stateContext?.delegate}, state: $state")
                stateInfo.stateContext = CachedStateContext(state)
            } else {
                Logger.d(TAG, "[updateStateLocked] skip update(equal stateContext) current: ${stateInfo.stateContext}, state: $state")
            }
            stateInfo.refreshPolicy = refreshPolicy
            ContextSetterInterface.SetStateResult.SUCCESS
        }
    }

    override fun getContext(
        contextRequester: ContextRequester,
        target: NamespaceAndName?,
        given: HashMap<NamespaceAndName, ContextState>?
    ) {
        synchronized(contextRequesterQueue) {
            contextRequesterQueue.offer(GetContextParam(contextRequester, target, given))

            if (contextRequesterQueue.isNotEmpty()) {
                updateStatesThread.wakeOne()
            }
        }
    }
    
    override fun getContextWithoutUpdate(namespaceAndName: NamespaceAndName?): String = stateProviderLock.withLock {
        buildContext(namespaceAndName)
    }
}
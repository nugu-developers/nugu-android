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

package com.skt.nugu.sdk.core.context

import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.concurrent.withLock

class ContextManager : ContextManagerInterface {
    companion object {
        private const val TAG = "ContextManager"
    }

    private data class GetContextParam(
        // input
        val contextRequester: ContextRequester,
        val target: NamespaceAndName?,
        val given: MutableMap<NamespaceAndName, BaseContextState>?,

        // intermediate
        var outdateFuture: ScheduledFuture<*>? = null,
        val pendings: MutableMap<NamespaceAndName, ContextType> = HashMap(),

        // for debug
        val requestedTimes: MutableMap<NamespaceAndName, Long> = HashMap(),

        // output
        val updated: MutableMap<NamespaceAndName, SetStateParam> = HashMap()
    )

    private data class StateInfo(
        var refreshPolicy: StateRefreshPolicy,
        val state: MutableMap<ContextType, CachedStateContext> = EnumMap(ContextType::class.java)
    )

    private data class CachedStateContext(val delegate: BaseContextState) : BaseContextState {
        private val cacheValue: String by lazy { delegate.value() }
        override fun value(): String = cacheValue
    }

    private data class SetStateParam(
        val state: BaseContextState,
        val refreshPolicy: StateRefreshPolicy,
        val type: ContextType
    )

    private val lock = ReentrantLock()
    private val stateProviders = HashMap<NamespaceAndName, ContextStateProvider>()
    private val namespaceNameToStateInfo = HashMap<NamespaceAndName, StateInfo>()
    private val namespaceToNameStateInfo: MutableMap<String, MutableMap<String, StateInfo>> = HashMap()

    private var stateRequestToken: Int = 0
    private val getContextRequestMap = HashMap<Int, GetContextParam>()

    private val stringBuilderForContext = StringBuilder(8192)

    private val callbackExecutor = Executors.newSingleThreadScheduledExecutor()
    private val provideStateExecutor = Executors.newFixedThreadPool(3)

    override fun setState(
        namespaceAndName: NamespaceAndName,
        state: BaseContextState,
        refreshPolicy: StateRefreshPolicy,
        type: ContextType,
        stateRequestToken: Int
    ): ContextSetterInterface.SetStateResult {
        lock.withLock {
            if (stateRequestToken == 0) {
                updateStateLocked(namespaceAndName, state, refreshPolicy, type)
                return ContextSetterInterface.SetStateResult.SUCCESS
            }

            val request = getContextRequestMap[stateRequestToken]
            if (request == null) {
                Logger.w(
                    TAG,
                    "[setState] outdated request for stateRequestToken: $stateRequestToken (no request)"
                )
                return ContextSetterInterface.SetStateResult.STATE_TOKEN_OUTDATED
            }

            val pendingType = request.pendings.remove(namespaceAndName)
            if (pendingType == null) {
                Logger.w(
                    TAG,
                    "[setState] outdated request for stateRequestToken: $stateRequestToken (no pending)"
                )
                return ContextSetterInterface.SetStateResult.STATE_TOKEN_OUTDATED
            }

            // TODO type check
            if(pendingType != type) {
                Logger.w(TAG, "[setState] InvalidType - request type: $pendingType, set type: $type")
            }

            updateStateLocked(namespaceAndName, state, refreshPolicy, type)
            request.updated[namespaceAndName] = SetStateParam(state, refreshPolicy, type)

            val timeTaken = System.currentTimeMillis() - (request.requestedTimes[namespaceAndName] ?: 0)
            val updatedState: CachedStateContext? = namespaceNameToStateInfo[namespaceAndName]?.state?.get(type)

            Logger.d(
                    TAG,
                    "[setState] success - namespaceAndName: $namespaceAndName, " +
                            "state: $updatedState, " +
                            "refreshPolicy: $refreshPolicy, " +
                            "type: $type, " +
                            "stateRequestToken: $stateRequestToken, " +
                            "(takes time to collect state: ${timeTaken}ms)"
            )

            if (request.pendings.isEmpty()) {
                request.outdateFuture?.cancel(true)
                callbackExecutor.submit {
                    Logger.d(TAG, "[setState] onContextAvailable token: $stateRequestToken")
                    val tempRequest = getContextRequestMap.remove(stateRequestToken)
                    tempRequest?.contextRequester?.onContextAvailable(
                        buildContextLocked(
                            tempRequest.target,
                            tempRequest.given
                        )
                    )
                }
            }
            return ContextSetterInterface.SetStateResult.SUCCESS
        }
    }

    private fun updateStateLocked(
        namespaceAndName: NamespaceAndName,
        state: BaseContextState,
        refreshPolicy: StateRefreshPolicy,
        type: ContextType
    ) {
        Logger.d(TAG, "[updateStateLocked] $namespaceAndName, $state, $refreshPolicy, $type")

        var stateInfo = namespaceNameToStateInfo[namespaceAndName]
        if(stateInfo == null) {
            stateInfo = StateInfo(refreshPolicy).apply {
                this.state[type] = CachedStateContext(state)
            }
            namespaceNameToStateInfo[namespaceAndName] = stateInfo
        } else {
            if(type == ContextType.FULL) {
                stateInfo.refreshPolicy = refreshPolicy
            }
            stateInfo.state[type] = CachedStateContext(state)
        }

        var nameAndStateInfo = namespaceToNameStateInfo[namespaceAndName.namespace]
        if(nameAndStateInfo == null) {
            nameAndStateInfo = HashMap()
            namespaceToNameStateInfo[namespaceAndName.namespace] = nameAndStateInfo
        }
        nameAndStateInfo[namespaceAndName.name] = stateInfo
    }

    override fun getContext(
        contextRequester: ContextRequester,
        target: NamespaceAndName?,
        given: HashMap<NamespaceAndName, BaseContextState>?,
        timeoutInMillis: Long
    ) {
        lock.withLock {
            if (stateRequestToken == 0) {
                stateRequestToken++
            }

            val token = stateRequestToken++

            Logger.d(TAG, "[getContext] contextRequester: $contextRequester, target: $target, given: $given, timeoutInMillis: $timeoutInMillis, token: $token")


            val param = GetContextParam(
                contextRequester,
                target,
                given
            )
            getContextRequestMap[token] = param

            param.apply {
                stateProviders.forEach {
                    if (given == null || !given.containsKey(it.key)) {
                        // if not given
                        val stateInfo = namespaceNameToStateInfo[it.key]

                        val requiredContextType = if (target == null || it.key == target) {
                            ContextType.FULL
                        } else {
                            ContextType.COMPACT
                        }

                        if(stateInfo == null) {
                            pendings[it.key] = requiredContextType
                        } else {
                            if(stateInfo.state[requiredContextType] == null) {
                                pendings[it.key] = requiredContextType
                            } else if(stateInfo.refreshPolicy != StateRefreshPolicy.NEVER) {
                                pendings[it.key] = requiredContextType
                            }
                        }
                    }
                }

                outdateFuture = callbackExecutor.schedule({
                    Logger.d(TAG, "[getContext] outdated token: $token")
                    val request = lock.withLock { getContextRequestMap.remove(token) }
                    if (request != null) {
                        contextRequester.onContextFailure(
                            ContextRequester.ContextRequestError.STATE_PROVIDER_TIMEOUT,
                            lock.withLock { buildContextLocked(target, given) }
                        )
                    }
                }, timeoutInMillis, TimeUnit.MILLISECONDS)

                HashMap(pendings).forEach {
                    stateProviders[it.key]?.let { provider->
                        provideStateExecutor.submit {
                            requestedTimes[it.key] = System.currentTimeMillis()
                            provider.provideState(
                                this@ContextManager,
                                it.key,
                                it.value,
                                token
                            )
                        }
                    }
                }
            }
        }
    }

    override fun setStateProvider(
        namespaceAndName: NamespaceAndName,
        stateProvider: ContextStateProvider?
    ) {
        lock.withLock {
            Logger.d(TAG, "[setStateProvider] namespaceAndName: $namespaceAndName, stateProvider: $stateProvider")
            if(stateProvider == null) {
                stateProviders.remove(namespaceAndName)
                namespaceNameToStateInfo.remove(namespaceAndName)
                namespaceToNameStateInfo[namespaceAndName.namespace]?.remove(namespaceAndName.name)
            } else {
                stateProviders[namespaceAndName] = stateProvider
            }
        }
    }

    private fun buildContextLocked(target: NamespaceAndName?, given: Map<NamespaceAndName, BaseContextState>? = null): String = stringBuilderForContext.apply {
        Logger.d(TAG, "[buildContext] target: $target, given: $given")
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
                val fullState = it.value.state[ContextType.FULL]?.value()
                val compactState = it.value.state[ContextType.COMPACT]?.value()
                val givenContextState = given?.get(NamespaceAndName(namespaceEntry.key, it.key))
                val value: String? = if (target == null || (target.namespace == namespaceEntry.key && target.name == it.key) || compactState == null) {
                    // need full
                    // full case
                    givenContextState?.value() ?: fullState
                } else {
                    givenContextState?.value() ?: compactState
                }

                if (value == null || (value.isEmpty() && it.value.refreshPolicy == StateRefreshPolicy.SOMETIMES)) {
                    // pass
                } else {
                    if (valueIndex > 0) {
                        append(',')
                    }
                    valueIndex++

                    append("\"${it.key}\":")
                    append(value)
                }
            }
            append('}')
        }
        append('}')
    }.toString()

    fun refreshAllState() {
        lock.withLock {
            stateProviders.forEach {
                provideStateExecutor.submit {
                    it.value.provideState(this, it.key, ContextType.FULL, 0)
                    it.value.provideState(this, it.key, ContextType.COMPACT, 0)
                }
            }
        }
    }
}

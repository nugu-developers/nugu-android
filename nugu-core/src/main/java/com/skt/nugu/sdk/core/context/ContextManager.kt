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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.LoopThread
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.concurrent.withLock

class ContextManager : ContextManagerInterface {
    companion object {
        private const val TAG = "ContextManager"
        const val SUPPORTED_INTERFACES = "supportedInterfaces"
    }

    private data class StateInfo(
        var stateProvider: ContextStateProvider?,
        var jsonState: String = "",
        var refreshPolicy: StateRefreshPolicy = StateRefreshPolicy.ALWAYS
    )

    private val namespaceNameToStateInfo: MutableMap<NamespaceAndName, StateInfo> = HashMap()
    private val contextRequesterQueue: Queue<Pair<ContextRequester, NamespaceAndName?>> =
        LinkedList()
    private val pendingOnStateProviders = HashSet<NamespaceAndName>()
    private val stateProviderLock = ReentrantLock()
    private var stateRequestToken: Int = 0
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
                sendContextAndClearQueue(
                    null,
                    ContextRequester.ContextRequestError.STATE_PROVIDER_TIMEOUT
                )
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
                StateRefreshPolicy.SOMETHIMES == stateInfo.refreshPolicy
            ) {
                pendingOnStateProviders.add(it.key)
                stateInfo.stateProvider?.provideState(this, it.key, curStateReqToken)
            }
        }
    }

    private fun sendContextToRequesters() {
        Logger.d(TAG, "[sendContextToRequesters]")

        val jsonContext = stateProviderLock.withLock {
            buildContext()
        }

        sendContextAndClearQueue(jsonContext, null)
    }

    private fun buildContext(): JsonObject {
        Logger.d(TAG, "[buildContext]")
        return JsonObject().apply {
            val supportedInterfaces = JsonObject()
            val client = JsonObject()

            add(SUPPORTED_INTERFACES, supportedInterfaces)
            add("client", client)

            for (it in namespaceNameToStateInfo) {
                if (it.value.jsonState.isEmpty() && StateRefreshPolicy.SOMETHIMES == it.value.refreshPolicy) {
                    // pass
                } else {
                    if (it.key.namespace == SUPPORTED_INTERFACES) {
                        supportedInterfaces.add(
                            it.key.name,
                            JsonParser().parse(it.value.jsonState).asJsonObject
                        )
                    } else if (it.key.namespace == "client") {
                        client.add(it.key.name, JsonParser().parse(it.value.jsonState))
                    }
                }
            }
        }
    }

    private fun sendContextAndClearQueue(
        context: JsonObject?,
        contextRequestError: ContextRequester.ContextRequestError?
    ) {
        Logger.d(TAG, "[sendContextAndClearQueue]")
        val strAllContext = context.toString()
        val jsonParser = JsonParser()

        synchronized(contextRequesterQueue) {
            while (contextRequesterQueue.isNotEmpty()) {
                with(contextRequesterQueue.poll()) {
                    if (context != null) {
                        val namespaceAndName = second
                        val strContext = if (namespaceAndName == null) {
                            strAllContext
                        } else {
                            val filterOutContext = filterOutContextBy(namespaceAndName, jsonParser.parse(strAllContext).asJsonObject)
                            filterOutContext.toString()
                        }
                        first.onContextAvailable(strContext)
                    } else {
                        first.onContextFailure(contextRequestError!!)
                    }
                }
            }
        }
    }

    private fun filterOutContextBy(
        namespaceAndName: NamespaceAndName,
        context: JsonObject
    ): JsonObject {
        val namespaceJsonObject = context.getAsJsonObject(namespaceAndName.namespace)
        val entrySet = namespaceJsonObject.entrySet()
        val shouldBeRemoveKeys = HashSet<String>()
        entrySet.forEach {
            if (it.key != namespaceAndName.name) {
                shouldBeRemoveKeys.add(it.key)
            }
        }
        shouldBeRemoveKeys.forEach {
            namespaceJsonObject.remove(it)
        }

        return context
    }

    override fun setStateProvider(
        namespaceAndName: NamespaceAndName,
        stateProvider: ContextStateProvider?
    ) {
        stateProviderLock.withLock {
            if (stateProvider == null) {
                namespaceNameToStateInfo.remove(namespaceAndName)
            } else {
                val stateInfo = namespaceNameToStateInfo[namespaceAndName]
                if (stateInfo == null) {
                    namespaceNameToStateInfo[namespaceAndName] = StateInfo(stateProvider)
                } else {
                    stateInfo.stateProvider = stateProvider
                }
            }
        }
    }

    override fun setState(
        namespaceAndName: NamespaceAndName,
        jsonState: String,
        refreshPolicy: StateRefreshPolicy,
        stateRequestToken: Int
    ): ContextSetterInterface.SetStateResult {
        stateProviderLock.withLock {
            Logger.d(
                TAG,
                "[setState] namespaceAndName: $namespaceAndName, context: $jsonState, policy: $refreshPolicy, $stateRequestToken"
            )
            if (0 == stateRequestToken) {
                return updateStateLocked(namespaceAndName, jsonState, refreshPolicy)
            }

            if (stateRequestToken != this.stateRequestToken) {
                return ContextSetterInterface.SetStateResult.STATE_TOKEN_OUTDATED
            }

            val status = updateStateLocked(namespaceAndName, jsonState, refreshPolicy)
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
        jsonState: String,
        refreshPolicy: StateRefreshPolicy
    ): ContextSetterInterface.SetStateResult {
        val stateInfo = namespaceNameToStateInfo[namespaceAndName]

        return if (stateInfo == null) {
            return when (refreshPolicy) {
                StateRefreshPolicy.ALWAYS, StateRefreshPolicy.SOMETHIMES -> {
                    ContextSetterInterface.SetStateResult.STATE_PROVIDER_NOT_REGISTERED
                }
                StateRefreshPolicy.NEVER -> {
                    namespaceNameToStateInfo[namespaceAndName] =
                        StateInfo(null, jsonState, refreshPolicy)
                    ContextSetterInterface.SetStateResult.SUCCESS
                }
            }
        } else {
            stateInfo.jsonState = jsonState
            stateInfo.refreshPolicy = refreshPolicy
            ContextSetterInterface.SetStateResult.SUCCESS
        }
    }

    override fun getContext(
        contextRequester: ContextRequester,
        namespaceAndName: NamespaceAndName?
    ) {
        synchronized(contextRequesterQueue) {
            contextRequesterQueue.offer(Pair(contextRequester, namespaceAndName))

            if (contextRequesterQueue.isNotEmpty()) {
                updateStatesThread.wakeOne()
            }
        }
    }
}
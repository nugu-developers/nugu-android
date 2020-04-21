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

import com.google.gson.JsonElement
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
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.withLock

class ContextManager : ContextManagerInterface {
    companion object {
        private const val TAG = "ContextManager"
    }

    private data class StateInfo(
        var stateProvider: ContextStateProvider?,
        var compactState: JsonObject? = null,
        var fullState: JsonElement? = null,
        var refreshPolicy: StateRefreshPolicy = StateRefreshPolicy.ALWAYS,
        var updatedFlag: Boolean = false
    )

    private val namespaceNameToStateInfo: MutableMap<NamespaceAndName, StateInfo> = HashMap()
    private val contextRequesterQueue: Queue<Pair<ContextRequester, NamespaceAndName?>> =
        LinkedList()
    private val pendingOnStateProviders = HashSet<NamespaceAndName>()
    private val stateProviderLock = ReentrantLock()
    private var stateRequestToken: Int = 0
    private var lastBuiltJsonContext: JsonObject? = null


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
                sendContextFailureAndClearQueue(
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
                StateRefreshPolicy.SOMETIMES == stateInfo.refreshPolicy
            ) {
                pendingOnStateProviders.add(it.key)
                stateInfo.stateProvider?.provideState(this, it.key, curStateReqToken)
            }
        }
    }

    private fun sendContextToRequesters() {
        Logger.d(TAG, "[sendContextToRequesters]")

        val context = stateProviderLock.withLock {
            buildContext()
        }

        Logger.d(TAG, "[sendContextAndClearQueue]")
        synchronized(contextRequesterQueue) {
            val fullContext = if(contextRequesterQueue.any {
                it.second == null
            }) {
                stateProviderLock.withLock {
                    context.toString()
                }
            } else {
                null
            }

            while (contextRequesterQueue.isNotEmpty()) {
                with(contextRequesterQueue.poll()) {
                    val namespaceAndName = second
                    val strContext: String = if(namespaceAndName == null) {
                        fullContext!!
                    } else {
                        stateProviderLock.withLock {
                            buildCompactContextFrom(context, namespaceAndName).toString()
                        }
                    }
                    first.onContextAvailable(strContext)
                }
            }
        }
    }

    private fun buildContext(): JsonObject {
        val tempJsonContext = lastBuiltJsonContext
        return if(tempJsonContext != null) {
            // update context should be updated
            val namespaceAndNameMap = HashMap<String, MutableSet<String>>()
            for (it in namespaceNameToStateInfo) {
                var set = namespaceAndNameMap[it.key.namespace]
                if(set == null) {
                    set = HashSet()
                    namespaceAndNameMap[it.key.namespace] = set
                }
                set.add(it.key.name)
                if (!it.value.updatedFlag) {
                    updateFullStateInfoAt(tempJsonContext, it, true)
                }
            }
            Logger.d(TAG, "[buildContext] exist last built json context.")

//            // remove context should be removed
            namespaceAndNameMap.forEach {
                val jsonObject = tempJsonContext.get(it.key).asJsonObject
                jsonObject.entrySet().filterNot { entry->
                    it.value.contains(entry.key)
                }.forEach { removeItem->
                    jsonObject.remove(removeItem.key)
                }
//                val shouldBeRemoved = HashSet<String>()
//                val jsonObject = tempJsonContext.get(it.key).asJsonObject
//                jsonObject.entrySet().forEach { entry ->
//                    if (!it.value.contains(entry.key)) {
//                        shouldBeRemoved.add(entry.key)
//                    }
//                }
//
//                shouldBeRemoved.forEach { removeKey ->
//                    jsonObject.remove(removeKey)
//                }
            }

            tempJsonContext
        } else {
            Logger.d(TAG, "[buildContext] first build context")
            // create context
            JsonObject().apply {
                for (it in namespaceNameToStateInfo) {
                    updateFullStateInfoAt(this, it, true)
                }

                lastBuiltJsonContext = this
            }
        }
    }

    private fun updateFullStateInfoAt(
        jsonObject: JsonObject,
        state: MutableMap.MutableEntry<NamespaceAndName, StateInfo>,
        updateFlag: Boolean
    ) {
        with(jsonObject) {
            if (state.value.fullState == null && StateRefreshPolicy.SOMETIMES == state.value.refreshPolicy) {
                // pass
            } else {
                var namespaceJsonObject = getAsJsonObject(state.key.namespace)
                if (namespaceJsonObject == null) {
                    namespaceJsonObject = JsonObject()
                    add(state.key.namespace, namespaceJsonObject)
                }
                namespaceJsonObject.add(state.key.name, state.value.fullState)
            }
            if(updateFlag) {
                state.value.updatedFlag = true
            }
        }
    }

    private fun updateCompactStateInfoAt(jsonObject: JsonObject, state: MutableMap.MutableEntry<NamespaceAndName, StateInfo>) {
        with(jsonObject) {
            if (state.value.fullState == null && StateRefreshPolicy.SOMETIMES == state.value.refreshPolicy) {
                // pass
            } else {
                var namespaceJsonObject = getAsJsonObject(state.key.namespace)
                if (namespaceJsonObject == null) {
                    namespaceJsonObject = JsonObject()
                    add(state.key.namespace, namespaceJsonObject)
                }
                if(state.value.compactState != null) {
                    namespaceJsonObject.add(state.key.name, state.value.compactState)
                } else {
                    namespaceJsonObject.add(state.key.name, state.value.fullState)
                }
            }
        }
    }

    private fun sendContextFailureAndClearQueue(
        contextRequestError: ContextRequester.ContextRequestError
    ) {
        Logger.d(TAG, "[sendContextAndClearQueue]")
        synchronized(contextRequesterQueue) {
            while (contextRequesterQueue.isNotEmpty()) {
                with(contextRequesterQueue.poll()) {
                    first.onContextFailure(contextRequestError)
                }
            }
        }
    }

    override fun setStateProvider(
        namespaceAndName: NamespaceAndName,
        stateProvider: ContextStateProvider?,
        compactState: String?
    ) {
        stateProviderLock.withLock {
            if (stateProvider == null) {
                namespaceNameToStateInfo.remove(namespaceAndName)
            } else {
                val stateInfo = namespaceNameToStateInfo[namespaceAndName]
                if (stateInfo == null) {
                    namespaceNameToStateInfo[namespaceAndName] = StateInfo(stateProvider, if(compactState != null) JsonParser().parse(compactState).asJsonObject else null)
                } else {
                    stateInfo.stateProvider = stateProvider
                    stateInfo.compactState = if(compactState != null) JsonParser().parse(compactState).asJsonObject else null
                }
            }
        }
    }

    override fun setState(
        namespaceAndName: NamespaceAndName,
        jsonState: String?,
        refreshPolicy: StateRefreshPolicy,
        stateRequestToken: Int
    ): ContextSetterInterface.SetStateResult {
        stateProviderLock.withLock {
            if(jsonState == null) {
                Logger.d(
                    TAG,
                    "[setState] namespaceAndName: $namespaceAndName, state: $jsonState, currentState: ${namespaceNameToStateInfo[namespaceAndName]?.fullState}, policy: $refreshPolicy, $stateRequestToken"
                )
            } else {
                Logger.d(
                    TAG,
                    "[setState] namespaceAndName: $namespaceAndName, state: $jsonState, policy: $refreshPolicy, $stateRequestToken"
                )
            }
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
        jsonState: String?,
        refreshPolicy: StateRefreshPolicy
    ): ContextSetterInterface.SetStateResult {
        val stateInfo = namespaceNameToStateInfo[namespaceAndName]

        return if (stateInfo == null) {
            return when (refreshPolicy) {
                StateRefreshPolicy.ALWAYS, StateRefreshPolicy.SOMETIMES -> {
                    ContextSetterInterface.SetStateResult.STATE_PROVIDER_NOT_REGISTERED
                }
                StateRefreshPolicy.NEVER -> {
                    jsonState?.let {
                        namespaceNameToStateInfo[namespaceAndName] =
                            StateInfo(null, null, if(jsonState.isNotEmpty()) {
                                JsonParser().parse(jsonState)
                            } else {
                                null
                            }, refreshPolicy)
                    }
                    ContextSetterInterface.SetStateResult.SUCCESS
                }
            }
        } else {
            jsonState?.let {
                // only update when not null
                if(jsonState.isNotEmpty()) {
                    stateInfo.fullState = JsonParser().parse(jsonState)
                } else {
                    stateInfo.fullState = null
                }
                stateInfo.updatedFlag = false
            }
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

    override fun getContextWithoutUpdate(namespaceAndName: NamespaceAndName?): String = stateProviderLock.withLock {
        buildCompactContextFrom(buildContext(), namespaceAndName).toString()
    }

    private fun buildCompactContextFrom(context: JsonObject, namespaceAndName: NamespaceAndName?): JsonObject {
        return if(namespaceAndName == null) {
            context
        } else {
            try {
                JsonObject().apply {
                    for (it in namespaceNameToStateInfo) {
                        if(it.key != namespaceAndName) {
                            updateCompactStateInfoAt(this, it)
                        } else {
                            updateFullStateInfoAt(this, it, false)
                        }
                    }
                }
            } catch (th: Throwable) {
                context
            }
        }
    }
}
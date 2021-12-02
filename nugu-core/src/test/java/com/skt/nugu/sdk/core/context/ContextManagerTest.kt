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

package com.skt.nugu.sdk.core.context

import com.google.gson.JsonObject
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.log.LogInterface
import com.skt.nugu.sdk.core.utils.Logger
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.*

class ContextManagerTest {

    private val audioPlayerContextProvider = object: SupportedInterfaceContextProvider {
        override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, "AudioPlayer")

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            contextType: ContextType,
            stateRequestToken: Int
        ) {
            Thread.sleep(10)
            contextSetter.setState(namespaceAndName, object: BaseContextState {
                override fun value(): String = JsonObject().apply {
                    addProperty("version", "1.0")
                    if(contextType == ContextType.FULL) {
                        addProperty("type", "test_full")
                    }
                }.toString()
            }, StateRefreshPolicy.ALWAYS, contextType, stateRequestToken)
        }
    }

    private val ttsContextProvider = object: SupportedInterfaceContextProvider {
        override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, "TTS")

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            contextType: ContextType,
            stateRequestToken: Int
        ) {
            Thread.sleep(10)
            contextSetter.setState(namespaceAndName, object: BaseContextState {
                override fun value(): String = JsonObject().apply {
                    addProperty("version", "1.0")
                    if(contextType == ContextType.FULL) {
                        addProperty("type", "test_full")
                    }
                }.toString()
            }, StateRefreshPolicy.ALWAYS, contextType, stateRequestToken)
        }
    }

    companion object {
        init {
            //initLogger()
        }

        private fun initLogger() {
            Logger.logger = object: LogInterface {
                override fun d(tag: String, msg: String, throwable: Throwable?) {
                    println("[DEBUG] $tag:$msg")
                }

                override fun e(tag: String, msg: String, throwable: Throwable?) {
                    println("[ERROR] $tag:$msg")
                }

                override fun w(tag: String, msg: String, throwable: Throwable?) {
                    println("[WARN] $tag:$msg")
                }

                override fun i(tag: String, msg: String, throwable: Throwable?) {
                    println("[INFO] $tag:$msg")
                }
            }
        }
    }


    @Test
    fun testSetStateWithoutProvider() {
        val manager = ContextManager()
        Assert.assertEquals(
            manager.setState(
                audioPlayerContextProvider.namespaceAndName,
                mock(),
                StateRefreshPolicy.ALWAYS,
                ContextType.FULL,
                0
            ), ContextSetterInterface.SetStateResult.SUCCESS
        )
    }

    @Test
    fun testSetStateWithInvalidToken() {
        val manager = ContextManager()
        Assert.assertEquals(
            manager.setState(
                audioPlayerContextProvider.namespaceAndName,
                mock(),
                StateRefreshPolicy.ALWAYS,
                ContextType.FULL,
                1
            ), ContextSetterInterface.SetStateResult.STATE_TOKEN_OUTDATED
        )
    }

    @Test
    fun testGetContext() {
        val manager = ContextManager()
        manager.setStateProvider(audioPlayerContextProvider.namespaceAndName, audioPlayerContextProvider)
        val contextRequester: ContextRequester = mock()
        manager.getContext(contextRequester)
        verify(contextRequester, timeout(1000)).onContextAvailable(any())
    }

    @Test
    fun testGetContextWithTimeout() {
        val manager = ContextManager()
        manager.setStateProvider(audioPlayerContextProvider.namespaceAndName, audioPlayerContextProvider)
        val contextRequester: ContextRequester = mock()
        manager.getContext(contextRequester, timeoutInMillis = 1)
        verify(contextRequester, timeout(1000)).onContextFailure(eq(ContextRequester.ContextRequestError.STATE_PROVIDER_TIMEOUT), any())
    }

    @Test
    fun testGetContextWithTarget() {
        val manager = ContextManager()
        manager.setStateProvider(ttsContextProvider.namespaceAndName, ttsContextProvider)
        manager.setStateProvider(audioPlayerContextProvider.namespaceAndName, audioPlayerContextProvider)
        val contextRequester: ContextRequester = mock()
        manager.getContext(contextRequester, target = ttsContextProvider.namespaceAndName)
        verify(contextRequester, timeout(1000)).onContextAvailable(eq("{\"supportedInterfaces\":{\"TTS\":{\"version\":\"1.0\",\"type\":\"test_full\"},\"AudioPlayer\":{\"version\":\"1.0\"}}}"))
    }
}
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

package com.skt.nugu.sdk.agent.location

import com.google.gson.JsonParser
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextType
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.Executors

class LocationAgentTest {
    @Test
    fun testRegisteredAtContextManager() {
        val contextManager: ContextManagerInterface = mock()
        val agent = LocationAgent(contextManager, mock())
        verify(contextManager).setStateProvider(agent.namespaceAndName, agent)
    }

    @Test
    fun testGetInterfaceName() {
        val agent = LocationAgent(mock(), mock())
        Assert.assertTrue(agent.namespaceAndName.name == LocationAgent.NAMESPACE)
    }

    @Test
    fun testProvideStateWithCompactContext() {
        val agent = LocationAgent(mock(), mock())
        val contextSetter: ContextSetterInterface = mock()

        val namespaceAndName = NamespaceAndName("", "")
        val token = 1

        agent.provideState(contextSetter, namespaceAndName, ContextType.COMPACT, token)

        Executors.newSingleThreadExecutor().submit {
            verify(contextSetter, times(1)).setState(
                eq(namespaceAndName),
                eq(LocationAgent.StateContext.CompactContextState),
                any(),
                eq(ContextType.COMPACT),
                eq(token)
            )
        }.get()
    }

    @Test
    fun testProvideStateWithFullContext() {
        val provider: LocationProvider = mock()
        whenever(provider.getLocation()).thenReturn(Location("10","20"))

        val agent = LocationAgent(mock(), provider)
        val contextSetter: ContextSetterInterface = mock()

        val namespaceAndName = NamespaceAndName("", "")
        val token = 1
        val state = LocationAgent.StateContext(provider.getLocation())

        agent.provideState(contextSetter, namespaceAndName, ContextType.FULL, token)

        Executors.newSingleThreadExecutor().submit {
            verify(contextSetter, atLeastOnce()).setState(
                eq(namespaceAndName),
                eq(state),
                any(),
                eq(ContextType.FULL),
                eq(token)
            )
        }.get()
    }

    @Test
    fun testStateContextForVersion() {
        val json = JsonParser.parseString(LocationAgent.StateContext(null).value())
        Assert.assertTrue(json.asJsonObject["version"].asString == LocationAgent.VERSION.toString())
    }

    @Test
    fun testStateContextWithNullLocation() {
        val json = JsonParser.parseString(LocationAgent.StateContext(null).value())
        Assert.assertFalse(json.asJsonObject.has("current"))
    }

    @Test
    fun testStateContextWithLocation() {
        val json = JsonParser.parseString(LocationAgent.StateContext(Location("10","20")).value())
        Assert.assertTrue(json.asJsonObject["current"].asJsonObject.has("latitude"))
        Assert.assertTrue(json.asJsonObject["current"].asJsonObject.has("longitude"))
    }
}
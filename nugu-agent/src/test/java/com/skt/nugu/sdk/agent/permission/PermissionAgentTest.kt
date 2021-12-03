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

package com.skt.nugu.sdk.agent.permission

import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextType
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.*
import java.util.HashMap
import java.util.concurrent.Executors

class PermissionAgentTest {
    @Test
    fun testRegisteredAtContextManager() {
        val contextManager: ContextManagerInterface = mock()
        val agent = PermissionAgent(contextManager, mock())
        verify(contextManager).setStateProvider(agent.namespaceAndName, agent)
    }

    @Test
    fun testGetInterfaceName() {
        val agent = PermissionAgent(mock(), mock())
        Assert.assertTrue(agent.namespaceAndName.name == PermissionAgent.NAMESPACE)
    }

    @Test
    fun testProvideStateWithCompactContext() {
        val agent = PermissionAgent(mock(), mock())
        val contextSetter: ContextSetterInterface = mock()

        val namespaceAndName = NamespaceAndName("", "")
        val token = 1

        agent.provideState(contextSetter, namespaceAndName, ContextType.COMPACT, token)

        verify(contextSetter, timeout(1000)).setState(
            eq(namespaceAndName),
            eq(PermissionAgent.StateContext.CompactContextState),
            any(),
            eq(ContextType.COMPACT),
            eq(token)
        )
    }

    @Test
    fun testProvideStateWithFullContext() {
        val delegate: PermissionDelegate = mock()
        whenever(delegate.supportedPermissions).thenReturn(arrayOf(PermissionType.LOCATION))
        whenever(delegate.getPermissionState(PermissionType.LOCATION)).thenReturn(PermissionState.DENIED)

        val agent = PermissionAgent(mock(), delegate)
        val contextSetter: ContextSetterInterface = mock()

        val namespaceAndName = NamespaceAndName("", "")
        val token = 1

        agent.provideState(contextSetter, namespaceAndName, ContextType.FULL, token)

        verify(contextSetter, timeout(1000)).setState(
            namespaceAndName,
            PermissionAgent.StateContext(HashMap<PermissionType, PermissionState>().apply {
                delegate.supportedPermissions.forEach {
                    put(it, delegate.getPermissionState(it))
                }
            }),
            StateRefreshPolicy.ALWAYS,
            ContextType.FULL,
            token
        )
    }

    @Test
    fun testRequestPermission() {
        val delegate: PermissionDelegate = mock()
        val agent = PermissionAgent(mock(), delegate)
        val payload: RequestPermissionDirectiveHandler.Payload =
            RequestPermissionDirectiveHandler.Payload(
                "playServiceId",
                arrayOf(PermissionType.LOCATION)
            )

        agent.requestPermission(Header("", "", "", "", "", ""), payload)

        verify(delegate, timeout(1000)).requestPermissions(payload.permissions)
    }
}
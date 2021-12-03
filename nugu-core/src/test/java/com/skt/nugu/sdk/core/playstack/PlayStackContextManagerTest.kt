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

import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextType
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.*

class PlayStackContextManagerTest {
    @Test
    fun testRegisteredAtContextManager() {
        val contextManager: ContextManagerInterface = mock()
        val contextProvider = PlayStackContextManager(contextManager, mock())
        verify(contextManager).setStateProvider(contextProvider.namespaceAndName, contextProvider)
    }

    @Test
    fun testGetName() {
        val contextProvider = PlayStackContextManager(mock(), mock())
        Assert.assertTrue(contextProvider.namespaceAndName.name == PlayStackContextManager.PROVIDER_NAME)
    }

    @Test
    fun testContextValue() {
        Assert.assertTrue(PlayStackContextManager.StateContext(arrayListOf("playServiceId")).value() == "[\"playServiceId\"]")
    }

    @Test
    fun testPlayStackContext() {
        val audioPlayStackProvider = PlayStackManager("audio").apply {
            addPlayContextProvider(object : PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                    PlayStackManagerInterface.PlayContext("playServiceId_5", 5)
            })
            addPlayContextProvider(object : PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                    PlayStackManagerInterface.PlayContext("playServiceId_2", 2, isBackground = true)
            })
            addPlayContextProvider(object : PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                    PlayStackManagerInterface.PlayContext("playServiceId_1", 1)
            })
        }
        val visualPlayStackProvider = PlayStackManager("visual").apply {
            addPlayContextProvider(object : PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                    PlayStackManagerInterface.PlayContext("playServiceId_6", 6)
            })
            addPlayContextProvider(object : PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                    PlayStackManagerInterface.PlayContext("playServiceId_4", 4, isBackground = true)
            })
            addPlayContextProvider(object : PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                    PlayStackManagerInterface.PlayContext("playServiceId_3", 3)
            })
        }

        val contextProvider =
            PlayStackContextManager(mock(), audioPlayStackProvider, visualPlayStackProvider)
        val contextSetter: ContextSetterInterface = mock()

        val token = 1

        contextProvider.provideState(
            contextSetter,
            contextProvider.namespaceAndName,
            ContextType.FULL,
            token
        )

        verify(contextSetter, timeout(1000)).setState(
            eq(contextProvider.namespaceAndName),
            eq(
                PlayStackContextManager.StateContext(
                    arrayListOf(
                        "playServiceId_6",
                        "playServiceId_5",
                        "playServiceId_4",
                        "playServiceId_3",
                        "playServiceId_2",
                        "playServiceId_1"
                    )
                )
            ),
            any(),
            eq(ContextType.FULL),
            eq(token)
        )
    }
}

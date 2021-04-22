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

import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import org.junit.Assert
import org.junit.Test

class PlayStackManagerTest {
    @Test
    fun testAddProvider() {
        val playStackManager = PlayStackManager("test")

        playStackManager.addPlayContextProvider(object :
            PlayStackManagerInterface.PlayContextProvider {
            override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                PlayStackManagerInterface.PlayContext("playServiceId_1", 1)
        })

        val stack = playStackManager.getPlayStack()
        Assert.assertTrue(stack.size == 1)
        Assert.assertTrue(stack.first().playServiceId == "playServiceId_1")
    }

    @Test
    fun testRemoveProvider() {
        val playStackManager = PlayStackManager("test")

        val provider = object :
            PlayStackManagerInterface.PlayContextProvider {
            override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                PlayStackManagerInterface.PlayContext("playServiceId_1", 1)
        }

        playStackManager.addPlayContextProvider(provider)
        playStackManager.removePlayContextProvider(provider)

        Assert.assertTrue(playStackManager.getPlayStack().isEmpty())
    }

    @Test
    fun testNonPersistentRemoval() {
        val playStackManager = PlayStackManager("test")

        playStackManager.addPlayContextProvider(object :
            PlayStackManagerInterface.PlayContextProvider {
            override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                PlayStackManagerInterface.PlayContext("playServiceId_1", 1)
        })
        playStackManager.addPlayContextProvider(object :
            PlayStackManagerInterface.PlayContextProvider {
            override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                PlayStackManagerInterface.PlayContext("playServiceId_2", 2, persistent = false)
        })

        val stack = playStackManager.getPlayStack()
        Assert.assertTrue(stack.size == 1)
        Assert.assertTrue(stack.first().playServiceId == "playServiceId_1")
    }

    @Test
    fun testNonAffectPersistent() {
        val playStackManager = PlayStackManager("test")

        playStackManager.addPlayContextProvider(object :
            PlayStackManagerInterface.PlayContextProvider {
            override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                PlayStackManagerInterface.PlayContext("playServiceId_1", 1, affectPersistent = false)
        })

        playStackManager.addPlayContextProvider(object :
            PlayStackManagerInterface.PlayContextProvider {
            override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                PlayStackManagerInterface.PlayContext("playServiceId_2", 2, affectPersistent = false)
        })

        Assert.assertTrue(playStackManager.getPlayStack().size == 2)
    }

    @Test
    fun testDescendingOrder() {
        val playStackManager = PlayStackManager("test")

        for (i in 0..3) {
            val index = i * 2 + 0
            playStackManager.addPlayContextProvider(object :
                PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                    PlayStackManagerInterface.PlayContext("playServiceId_$index", index.toLong())
            })
        }

        for (i in 0..3) {
            val index = i * 2 + 1
            playStackManager.addPlayContextProvider(object :
                PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext =
                    PlayStackManagerInterface.PlayContext("playServiceId_$index", index.toLong())
            })
        }

        val stack = playStackManager.getPlayStack()

        stack.forEachIndexed { index, playStackContext ->
            Assert.assertTrue((stack.size - 1) - index.toLong() == playStackContext.timestamp)
        }
    }
}
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
    fun testGetPlayStack() {
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
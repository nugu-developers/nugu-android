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

import org.mockito.kotlin.*
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.playstack.PlayStackManager
import org.junit.Test

class PlayStackContextManagerTest {
    @Test
    fun testDescendingOrder() {
        val audioPlayStackProvider = PlayStackManager("Audio")
        audioPlayStackProvider.apply {
            addPlayContextProvider(object: PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext? = PlayStackManagerInterface.PlayContext("first", 200, false)
            })
            addPlayContextProvider(object: PlayStackManagerInterface.PlayContextProvider {
                override fun getPlayContext(): PlayStackManagerInterface.PlayContext? = PlayStackManagerInterface.PlayContext("second", 100, false)
            })
        }
        val manager = PlayStackContextManager(mock(), audioPlayStackProvider, null)
        assert(manager.buildPlayStack().first() == "first")
    }
}

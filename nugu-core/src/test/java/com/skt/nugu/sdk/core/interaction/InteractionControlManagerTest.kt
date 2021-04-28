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

package com.skt.nugu.sdk.core.interaction

import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControl
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlMode
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyZeroInteractions

class InteractionControlManagerTest {
    private val manager: InteractionControlManagerInterface = InteractionControlManager()

    @Test
    fun testMultiturnInteractionControl() {
        val listener:InteractionControlManagerInterface.Listener = mock()
        val interactionControl: InteractionControl = object: InteractionControl {
            override fun getMode(): InteractionControlMode = InteractionControlMode.MULTI_TURN
        }

        with(manager) {
            addListener(listener)
            start(interactionControl)
            verify(listener).onMultiturnStateChanged(true)
            finish(interactionControl)
            verify(listener).onMultiturnStateChanged(false)
        }
    }

    @Test
    fun testNoneInteractionControl() {
        val listener:InteractionControlManagerInterface.Listener = mock()
        val interactionControl: InteractionControl = object: InteractionControl {
            override fun getMode(): InteractionControlMode = InteractionControlMode.NONE
        }

        with(manager) {
            addListener(listener)
            start(interactionControl)
            finish(interactionControl)
            verifyZeroInteractions(listener)
        }
    }

    @Test
    fun  testRemoveListener() {
        val listener:InteractionControlManagerInterface.Listener = mock()
        val interactionControl: InteractionControl = object: InteractionControl {
            override fun getMode(): InteractionControlMode = InteractionControlMode.MULTI_TURN
        }

        with(manager) {
            addListener(listener)
            removeListener(listener)
            start(interactionControl)
            finish(interactionControl)
            verifyZeroInteractions(listener)
        }
    }
}
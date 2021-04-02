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

package com.skt.nugu.sdk.core.display

import org.mockito.kotlin.*
import com.skt.nugu.sdk.core.interfaces.display.InterLayerDisplayPolicyManager
import com.skt.nugu.sdk.core.interfaces.display.LayerType
import org.junit.Test

class InterLayerDisplayPolicyManagerTest {
    private val pushPlayServiceId1 = "pushPlayServiceId_1"
    private val pushPlayServiceId2 = "pushPlayServiceId_2"

    private val playServiceId1 = "playServiceId_1"
    private val dialogRequestId1 = "dialogRequestId_1"

    private val playServiceId2 = "playServiceId_2"
    private val dialogRequestId2 = "dialogRequestId_2"

    @Test
    fun testClearEvaporatableLayerOnNewDisplayLayerRendered() {
        val manager = InterLayerDisplayPolicyManagerImpl()

        val evaporatableInfolayer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        whenever(evaporatableInfolayer.getLayerType()).thenReturn(LayerType.INFO)

        manager.onDisplayLayerRendered(evaporatableInfolayer)

        val newLayer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        whenever(newLayer.getLayerType()).thenReturn(LayerType.MEDIA)

        manager.onDisplayLayerRendered(newLayer)

        verify(evaporatableInfolayer, times(1)).clear()
    }

    @Test
    fun testNonClearNonEvaporatableLayerOnNewDisplayLayerRendered() {
        val manager = InterLayerDisplayPolicyManagerImpl()

        val nonEvaporatableInfolayer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        whenever(nonEvaporatableInfolayer.getLayerType()).thenReturn(LayerType.CALL)

        manager.onDisplayLayerRendered(nonEvaporatableInfolayer)

        val newLayer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        whenever(newLayer.getLayerType()).thenReturn(LayerType.MEDIA)

        manager.onDisplayLayerRendered(newLayer)

        verify(nonEvaporatableInfolayer, never()).clear()
    }

    @Test
    fun testClearEvaporatableLayerOnNewPlayStarted() {
        val manager = InterLayerDisplayPolicyManagerImpl()

        val evaporatableInfolayer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        whenever(evaporatableInfolayer.getPushPlayServiceId()).thenReturn(playServiceId1)
        whenever(evaporatableInfolayer.getLayerType()).thenReturn(LayerType.INFO)

        manager.onDisplayLayerRendered(evaporatableInfolayer)

        val play1: InterLayerDisplayPolicyManager.PlayLayer = mock()
        whenever(play1.getPushPlayServiceId()).thenReturn(pushPlayServiceId2)
        whenever(play1.getDialogRequestId()).thenReturn(dialogRequestId2)

        manager.onPlayStarted(play1)

        verify(evaporatableInfolayer, times(1)).clear()
    }
}
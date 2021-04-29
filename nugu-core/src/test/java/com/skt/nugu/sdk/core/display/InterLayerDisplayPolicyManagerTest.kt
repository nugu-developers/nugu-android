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
import java.util.*

class InterLayerDisplayPolicyManagerTest {
    private val pushPlayServiceId1 = "pushPlayServiceId_1"
    private val pushPlayServiceId2 = "pushPlayServiceId_2"

    private val playServiceId1 = "playServiceId_1"
    private val dialogRequestId1 = "dialogRequestId_1"

    private val playServiceId2 = "playServiceId_2"
    private val dialogRequestId2 = "dialogRequestId_2"

    private val manager = InterLayerDisplayPolicyManagerImpl()

    private val nonEvaporatableLayerSet: Set<LayerType> = linkedSetOf(LayerType.MEDIA, LayerType.CALL, LayerType.NAVI)
    private val evaporatableLayerSet: Set<LayerType> = EnumSet.allOf(LayerType::class.java).apply {
        removeAll(nonEvaporatableLayerSet)
    }

    @Test
    fun testClearEvaporatableLayerOnNewDisplayLayerRendered() {
        evaporatableLayerSet.forEach { evaporatableLayerType ->
            nonEvaporatableLayerSet.forEach { nonEvaporatableLayerType ->
                val evaporatableInfolayer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
                whenever(evaporatableInfolayer.getLayerType()).thenReturn(evaporatableLayerType)

                manager.onDisplayLayerRendered(evaporatableInfolayer)

                val newLayer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
                whenever(newLayer.getLayerType()).thenReturn(nonEvaporatableLayerType)
                manager.onDisplayLayerRendered(newLayer)

                // verify
                verify(evaporatableInfolayer).clear()

                // clear all
                manager.onDisplayLayerCleared(evaporatableInfolayer)
                manager.onDisplayLayerCleared(newLayer)
            }
        }
    }

    @Test
    fun testNonClearNonEvaporatableLayerOnNewDisplayLayerRendered() {
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
        val evaporatableInfolayer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        whenever(evaporatableInfolayer.getPushPlayServiceId()).thenReturn(playServiceId1)
        whenever(evaporatableInfolayer.getLayerType()).thenReturn(LayerType.INFO)

        manager.onDisplayLayerRendered(evaporatableInfolayer)

        val nullPlayServiceIdPlay: InterLayerDisplayPolicyManager.PlayLayer = mock()
        whenever(nullPlayServiceIdPlay.getPushPlayServiceId()).thenReturn(null)
        manager.onPlayStarted(nullPlayServiceIdPlay)
        verifyZeroInteractions(evaporatableInfolayer)

        val play1: InterLayerDisplayPolicyManager.PlayLayer = mock()
        whenever(play1.getPushPlayServiceId()).thenReturn(pushPlayServiceId2)
        whenever(play1.getDialogRequestId()).thenReturn(dialogRequestId2)

        manager.onPlayStarted(play1)

        verify(evaporatableInfolayer).clear()
    }

    @Test
    fun testListenerCalled() {
        val layer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        val listener: InterLayerDisplayPolicyManager.Listener = mock()

        manager.addListener(listener)
        manager.onDisplayLayerRendered(layer)
        manager.onDisplayLayerCleared(layer)

        inOrder(listener, listener).apply {
            verify(listener).onDisplayLayerRendered(eq(layer))
            verify(listener).onDisplayLayerCleared(eq(layer))
        }
    }

    @Test
    fun testRemoveListener() {
        val layer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        val listener: InterLayerDisplayPolicyManager.Listener = mock()

        manager.addListener(listener)
        manager.removeListener(listener)
        manager.onDisplayLayerRendered(layer)
        manager.onDisplayLayerCleared(layer)

        verifyZeroInteractions(listener)
    }

    @Test
    fun testOnDisplayLayerRenderedCalledTwiceForALayer() {
        val layer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        val listener: InterLayerDisplayPolicyManager.Listener = mock()

        manager.addListener(listener)

        // call twice
        manager.onDisplayLayerRendered(layer)
        manager.onDisplayLayerRendered(layer)

        verify(listener).onDisplayLayerRendered(eq(layer))
    }

    @Test
    fun testOnSamePushPlayServiceIdPlayStarted() {
        val displayLayer: InterLayerDisplayPolicyManager.DisplayLayer = mock()
        whenever(displayLayer.getPushPlayServiceId()).thenReturn(pushPlayServiceId1)
        whenever(displayLayer.getDialogRequestId()).thenReturn(dialogRequestId1)
        manager.onDisplayLayerRendered(displayLayer)

        val play0: InterLayerDisplayPolicyManager.PlayLayer = mock()
        whenever(play0.getPushPlayServiceId()).thenReturn(pushPlayServiceId1)
        whenever(play0.getDialogRequestId()).thenReturn(dialogRequestId1)

        manager.onPlayStarted(play0)
        verify(displayLayer, never()).refresh()

        val play1: InterLayerDisplayPolicyManager.PlayLayer = mock()
        whenever(play1.getPushPlayServiceId()).thenReturn(pushPlayServiceId1)
        whenever(play1.getDialogRequestId()).thenReturn(dialogRequestId2)

        manager.onPlayStarted(play1)
        verify(displayLayer, timeout(100)).refresh()
        manager.onPlayFinished(play1)
        verify(displayLayer, timeout(100).times(2)).refresh()
    }
}
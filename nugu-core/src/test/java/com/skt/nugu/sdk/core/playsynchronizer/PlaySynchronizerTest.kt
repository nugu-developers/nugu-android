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

package com.skt.nugu.sdk.core.playsynchronizer

import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import org.junit.Test
import org.mockito.kotlin.*

class PlaySynchronizerTest {
    private val emptyPlayServiceId = ""
    private val nullPlayServiceId: String? = null

    private val playServiceId1 = "playServiceId_1"
    private val dialogRequestId1 = "dialogRequestId_1"

    private val playServiceId2 = "playServiceId_2"
    private val dialogRequestId2 = "dialogRequestId_2"

    @Test
    fun testSimpleReleaseSync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj1.dialogRequestId).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj2.dialogRequestId).thenReturn(dialogRequestId1)


        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1)
        synchronizer.startSync(syncObj2)
        synchronizer.releaseSync(syncObj1)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }

    @Test
    fun testSimpleReleaseSyncImmediately() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj1.dialogRequestId).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj2.dialogRequestId).thenReturn(dialogRequestId1)

        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1)
        synchronizer.startSync(syncObj2)
        synchronizer.releaseSyncImmediately(syncObj1)

        verify(syncObj2, times(1)).requestReleaseSync()
    }

    @Test
    fun testDifferentDialogRequestIdReleaseSync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj1.dialogRequestId).thenReturn(dialogRequestId2)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj2.dialogRequestId).thenReturn(dialogRequestId1)


        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1)
        synchronizer.startSync(syncObj2)
        synchronizer.releaseSync(syncObj1)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }

    @Test
    fun testEmptyPlayServiceIdReleaseSync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.playServiceId).thenReturn(emptyPlayServiceId)
        whenever(syncObj1.dialogRequestId).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj2.dialogRequestId).thenReturn(dialogRequestId1)


        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1)
        synchronizer.startSync(syncObj2)
        synchronizer.releaseSync(syncObj1)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }

    @Test
    fun testNullPlayServiceIdReleaseSync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.playServiceId).thenReturn(nullPlayServiceId)
        whenever(syncObj1.dialogRequestId).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj2.dialogRequestId).thenReturn(dialogRequestId1)


        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1)
        synchronizer.startSync(syncObj2)
        synchronizer.releaseSync(syncObj1)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }

    @Test
    fun testUnrelatedTwoPlaySync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj1.dialogRequestId).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.playServiceId).thenReturn(playServiceId2)
        whenever(syncObj2.dialogRequestId).thenReturn(dialogRequestId2)

        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1)
        synchronizer.startSync(syncObj2)
        synchronizer.releaseSync(syncObj1)

        verify(syncObj2, never()).requestReleaseSync()
    }

    @Test
    fun testOnlyDialogRequestIdSameTwoPlaySync() {
        // never happen case (undefined case)
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj1.dialogRequestId).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.playServiceId).thenReturn(playServiceId2)
        whenever(syncObj2.dialogRequestId).thenReturn(dialogRequestId1)

        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1)
        synchronizer.startSync(syncObj2)
        synchronizer.releaseSync(syncObj1, null)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }

    @Test
    fun testOnSyncStateChangedOfListener() {
        val listener: PlaySynchronizerInterface.Listener = spy()
        val synchronizer = PlaySynchronizer()
        synchronizer.addListener(listener)

        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj1.dialogRequestId).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.playServiceId).thenReturn(playServiceId1)
        whenever(syncObj2.dialogRequestId).thenReturn(dialogRequestId1)


        synchronizer.prepareSync(syncObj1)
        verify(listener).onSyncStateChanged(eq(setOf(syncObj1)), eq(emptySet()))

        synchronizer.prepareSync(syncObj2)
        verify(listener, atLeastOnce()).onSyncStateChanged(
            eq(setOf(syncObj1, syncObj2)),
            eq(emptySet())
        )

        synchronizer.startSync(syncObj1)
        verify(listener, atLeastOnce()).onSyncStateChanged(eq(setOf(syncObj2)), eq(setOf(syncObj1)))

        synchronizer.startSync(syncObj2)
        verify(listener, atLeastOnce()).onSyncStateChanged(
            eq(emptySet()),
            eq(setOf(syncObj1, syncObj2))
        )

        synchronizer.releaseSync(syncObj1)
        verify(listener, atLeastOnce()).onSyncStateChanged(
            eq(emptySet()),
            eq(setOf(syncObj2))
        )

        synchronizer.releaseSyncImmediately(syncObj2)
        verify(listener, atLeastOnce()).onSyncStateChanged(
            eq(emptySet()),
            eq(emptySet())
        )
    }
}
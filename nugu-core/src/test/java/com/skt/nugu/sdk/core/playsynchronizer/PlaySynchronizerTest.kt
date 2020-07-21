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

import com.nhaarman.mockito_kotlin.*
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import org.junit.Test

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
        whenever(syncObj1.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj1.getDialogRequestId()).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj2.getDialogRequestId()).thenReturn(dialogRequestId1)


        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1, null)
        synchronizer.startSync(syncObj2, null)
        synchronizer.releaseSync(syncObj1, null)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }

    @Test
    fun testSimpleReleaseSyncImmediately() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj1.getDialogRequestId()).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj2.getDialogRequestId()).thenReturn(dialogRequestId1)

        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1, null)
        synchronizer.startSync(syncObj2, null)
        synchronizer.releaseSyncImmediately(syncObj1, null)

        verify(syncObj2, times(1)).requestReleaseSync()
    }

    @Test
    fun testSimpleReleaseWithoutSync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj1.getDialogRequestId()).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj2.getDialogRequestId()).thenReturn(dialogRequestId1)

        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1, null)
        synchronizer.startSync(syncObj2, null)
        synchronizer.releaseWithoutSync(syncObj1)

        verify(syncObj2, never()).requestReleaseSync()
    }

    @Test
    fun testDifferentDialogRequestIdReleaseSync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj1.getDialogRequestId()).thenReturn(dialogRequestId2)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj2.getDialogRequestId()).thenReturn(dialogRequestId1)


        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1, null)
        synchronizer.startSync(syncObj2, null)
        synchronizer.releaseSync(syncObj1, null)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }

    @Test
    fun testEmptyPlayServiceIdReleaseSync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.getPlayServiceId()).thenReturn(emptyPlayServiceId)
        whenever(syncObj1.getDialogRequestId()).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj2.getDialogRequestId()).thenReturn(dialogRequestId1)


        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1, null)
        synchronizer.startSync(syncObj2, null)
        synchronizer.releaseSync(syncObj1, null)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }

    @Test
    fun testNullPlayServiceIdReleaseSync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.getPlayServiceId()).thenReturn(nullPlayServiceId)
        whenever(syncObj1.getDialogRequestId()).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj2.getDialogRequestId()).thenReturn(dialogRequestId1)


        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1, null)
        synchronizer.startSync(syncObj2, null)
        synchronizer.releaseSync(syncObj1, null)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }

    @Test
    fun testUnrelatedTwoPlaySync() {
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj1.getDialogRequestId()).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.getPlayServiceId()).thenReturn(playServiceId2)
        whenever(syncObj2.getDialogRequestId()).thenReturn(dialogRequestId2)

        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1, null)
        synchronizer.startSync(syncObj2, null)
        synchronizer.releaseSync(syncObj1, null)

        verify(syncObj2, never()).requestReleaseSync()
    }

    @Test
    fun testOnlyDialogRequestIdSameTwoPlaySync() {
        // never happen case (undefined case)
        val synchronizer = PlaySynchronizer()
        val syncObj1: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj1.getPlayServiceId()).thenReturn(playServiceId1)
        whenever(syncObj1.getDialogRequestId()).thenReturn(dialogRequestId1)

        val syncObj2: PlaySynchronizerInterface.SynchronizeObject = mock()
        whenever(syncObj2.getPlayServiceId()).thenReturn(playServiceId2)
        whenever(syncObj2.getDialogRequestId()).thenReturn(dialogRequestId1)

        synchronizer.prepareSync(syncObj1)
        synchronizer.prepareSync(syncObj2)
        synchronizer.startSync(syncObj1, null)
        synchronizer.startSync(syncObj2, null)
        synchronizer.releaseSync(syncObj1, null)

        verify(syncObj2, times(4)).onSyncStateChanged(any(), any())
    }
}
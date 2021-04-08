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

package com.skt.nugu.sdk.agent.beep

import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BeepPlaybackControllerTest {
    @Test
    fun testAddSourceFirst() {
        val controller = BeepPlaybackController()
        val source: BeepPlaybackController.Source = mock()
        controller.addSource(source)
        verify(source).play()

        controller.removeSource(source)
    }

    @Test
    fun testAddTwoSourceEqualPriority() {
        val controller = BeepPlaybackController()

        val source1: BeepPlaybackController.Source = mock()
        whenever(source1.priority).thenReturn(1)
        val source2: BeepPlaybackController.Source = mock()
        whenever(source2.priority).thenReturn(1)

        controller.addSource(source1)
        controller.addSource(source2)

        verify(source1).play()

        // removeSource called twice, to test whether a next source play called once.
        controller.removeSource(source1)
        controller.removeSource(source1)

        verify(source2).play()
        controller.removeSource(source2)
    }

    @Test
    fun testAddSourcesNonEqualPriority() {
        val controller = BeepPlaybackController()

        val source1: BeepPlaybackController.Source = mock()
        whenever(source1.priority).thenReturn(1)

        val source2: BeepPlaybackController.Source = mock()
        whenever(source2.priority).thenReturn(3)

        val source3: BeepPlaybackController.Source = mock()
        whenever(source3.priority).thenReturn(2)

        controller.addSource(source1)
        controller.addSource(source2)
        controller.addSource(source3)

        verify(source1).play()
        controller.removeSource(source1)
        verify(source3).play()
        controller.removeSource(source3)
        verify(source2).play()
        controller.removeSource(source2)
    }
}
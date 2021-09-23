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
package com.skt.nugu.sdk.client.port.transport.grpc2

import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import org.junit.Assert
import org.junit.Test

class TransportStateTest {
    @Test
    fun testConnecting() {
        val newDetailedState = TransportState.DetailedState.CONNECTING
        val state = TransportState()

        Assert.assertNotEquals(state.getDetailedState(), newDetailedState)
        Assert.assertNotEquals(state.getState(), ConnectionStatusListener.Status.CONNECTING)
        state.setDetailedState(
            newDetailedState,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getDetailedState(), newDetailedState)
        Assert.assertEquals(
            TransportState.fromDetailedState(newDetailedState),
            ConnectionStatusListener.Status.CONNECTING
        )
        Assert.assertTrue(state.isConnectedOrConnecting())
    }

    @Test
    fun testSetDetailedState() {
        val state = TransportState()
        state.setDetailedState(
            TransportState.DetailedState.CONNECTING,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getState(), ConnectionStatusListener.Status.CONNECTING)
        state.setDetailedState(
            TransportState.DetailedState.CONNECTING_REGISTRY,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getState(), ConnectionStatusListener.Status.CONNECTING)
        state.setDetailedState(
            TransportState.DetailedState.CONNECTING_DEVICEGATEWAY,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getState(), ConnectionStatusListener.Status.CONNECTING)
        state.setDetailedState(
            TransportState.DetailedState.RECONNECTING,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getState(), ConnectionStatusListener.Status.CONNECTING)
        state.setDetailedState(
            TransportState.DetailedState.HANDOFF,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getState(), ConnectionStatusListener.Status.CONNECTING)

        state.setDetailedState(
            TransportState.DetailedState.IDLE,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getState(), ConnectionStatusListener.Status.DISCONNECTED)

        state.setDetailedState(
            TransportState.DetailedState.FAILED,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getState(), ConnectionStatusListener.Status.DISCONNECTED)

        state.setDetailedState(
            TransportState.DetailedState.DISCONNECTING,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getState(), ConnectionStatusListener.Status.DISCONNECTED)

        state.setDetailedState(
            TransportState.DetailedState.DISCONNECTED,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getState(), ConnectionStatusListener.Status.DISCONNECTED)
    }

    @Test
    fun testConnected() {
        val newDetailedState = TransportState.DetailedState.CONNECTED
        val state = TransportState()

        Assert.assertNotEquals(state.getDetailedState(), newDetailedState)
        Assert.assertNotEquals(state.getState(), ConnectionStatusListener.Status.CONNECTED)
        state.setDetailedState(
            newDetailedState,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(state.getDetailedState(), newDetailedState)
        Assert.assertEquals(
            TransportState.fromDetailedState(newDetailedState),
            ConnectionStatusListener.Status.CONNECTED
        )
        Assert.assertTrue(state.isConnected())
    }
}
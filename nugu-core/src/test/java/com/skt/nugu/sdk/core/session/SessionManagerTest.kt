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

package com.skt.nugu.sdk.core.session

import org.mockito.kotlin.*
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import org.junit.Assert
import org.junit.Test

private const val sessionKey1 = "session_key_1"
private const val sessionKey2 = "session_key_2"

private val session1 = SessionManagerInterface.Session("session_1","playServiceId_1")

class SessionManagerTest {
    private val inactiveTimeoutForTest = 100L
    private val sessionManager = SessionManager(inactiveTimeoutForTest)

    @Test
    fun testSet() {
        sessionManager.set(sessionKey1, session1)
        Assert.assertTrue(sessionManager.getActiveSessions().isEmpty())
    }

    @Test
    fun testActivate() {
        val requester: SessionManagerInterface.Requester = mock()

        sessionManager.activate(sessionKey1, requester)
        Assert.assertTrue(sessionManager.getActiveSessions().isEmpty())
    }

    @Test
    fun testSessionActivate() {
        val requester: SessionManagerInterface.Requester = mock()

        val listener: SessionManagerInterface.Listener = mock()
        sessionManager.addListener(listener)

        sessionManager.set(sessionKey1, session1)
        verify(listener, never()).onSessionActivated(sessionKey1, session1)

        sessionManager.activate(sessionKey1, requester)
        verify(listener).onSessionActivated(sessionKey1, session1)
        Assert.assertTrue(sessionManager.getActiveSessions().size == 1)
    }

    @Test
    fun testSessionDeactivate() {
        val requester: SessionManagerInterface.Requester = mock()

        val listener: SessionManagerInterface.Listener = mock()
        sessionManager.addListener(listener)

        sessionManager.set(sessionKey1, session1)
        sessionManager.activate(sessionKey1, requester)
        sessionManager.deactivate(sessionKey1, requester)
        Assert.assertTrue(sessionManager.getActiveSessions().isEmpty())
        verify(listener).onSessionActivated(sessionKey1, session1)
        verify(listener).onSessionDeactivated(sessionKey1, session1)
    }

    @Test
    fun testActivateAndSet() {
        val requester: SessionManagerInterface.Requester = mock()

        val listener: SessionManagerInterface.Listener = mock()
        sessionManager.addListener(listener)

        sessionManager.activate(sessionKey1, requester)
        verify(listener, never()).onSessionActivated(sessionKey1, session1)
        sessionManager.set(sessionKey1, session1)
        verify(listener).onSessionActivated(sessionKey1, session1)

        Thread.sleep(inactiveTimeoutForTest + 100L)
        Assert.assertTrue(sessionManager.getActiveSessions().isNotEmpty())
    }

    @Test
    fun testActivateTwoSessionUsingOneRequester() {
        val requester: SessionManagerInterface.Requester = mock()

        sessionManager.set(sessionKey1, session1)
        sessionManager.activate(sessionKey1, requester)
        sessionManager.set(sessionKey2, session1)
        sessionManager.activate(sessionKey2, requester)
        with(sessionManager.getActiveSessions()) {
            Assert.assertTrue(size == 1)
            Assert.assertTrue(containsKey(sessionKey2))
        }
    }

    @Test
    fun testActivateTwoSessionUsingOneRequester1() {
        val requester1: SessionManagerInterface.Requester = mock()
        val requester2: SessionManagerInterface.Requester = mock()

        val key1 = "0e01980a1d01a720bb4c8b7831154d74"
        val key2 = "0e0198322601a720bb4c8b787b0732b3"

        sessionManager.activate(key1, requester1)
        sessionManager.set(key1, session1)
        Thread.sleep(1)
        sessionManager.activate(key2, requester2)
        sessionManager.set(key2, session1)
        with(sessionManager.getActiveSessions()) {
            Assert.assertTrue(size == 1)
            Assert.assertTrue(containsKey(key2))
            Assert.assertFalse(containsKey(key1))
        }
    }
}
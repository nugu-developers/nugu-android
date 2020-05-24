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

import com.nhaarman.mockito_kotlin.mock
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import org.junit.Assert
import org.junit.Test

private const val sessionKey1= "session_key_1"

private val session1 = SessionManagerInterface.Session("session_1","playServiceId_1")

class SessionManagerTest {
    private val sessionManager = SessionManager()

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

        sessionManager.set(sessionKey1, session1)
        sessionManager.activate(sessionKey1, requester)
        Assert.assertTrue(sessionManager.getActiveSessions().size == 1)
    }

    @Test
    fun testSessionDeactivate() {
        val requester: SessionManagerInterface.Requester = mock()

        sessionManager.set(sessionKey1, session1)
        sessionManager.activate(sessionKey1, requester)
        sessionManager.deactivate(sessionKey1, requester)
        Assert.assertTrue(sessionManager.getActiveSessions().isEmpty())
    }
}
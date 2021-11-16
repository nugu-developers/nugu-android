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
package com.skt.nugu.sdk.platform.android.login.auth

import com.skt.nugu.sdk.platform.android.login.net.HttpClient
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import java.lang.RuntimeException
import java.util.*

class NuguOAuthClientTest {
    private val delegate = object : NuguOAuthClient.UrlDelegate {
        override fun baseUrl() = "baseUrl"
        override fun tokenEndpoint() = "endpoint"
        override fun authorizationEndpoint() = "endpoint"
        override fun introspectionEndpoint() = "endpoint"
        override fun revocationEndpoint() = "endpoint"
        override fun deviceAuthorizationEndpoint() = "endpoint"
        override fun meEndpoint() = "endpoint"
    }
    @Test
    fun testUrlDelegate() {
        Assert.assertEquals(delegate.baseUrl(),  "baseUrl")
    }

    @Test
    fun testHandleStopping() {
        val client = NuguOAuthClient(mock())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertEquals(client.handleStopping(), NuguOAuthClient.AuthFlowState.STOPPING)
    }

    @Test(expected = Throwable::class)
    fun testHandleStoppingException() {
        val client = NuguOAuthClient(mock())
        Assert.assertEquals(client.handleStopping(), NuguOAuthClient.AuthFlowState.STOPPING)
    }

    @Test
    fun testHandleStarting() {
        val client = NuguOAuthClient(mock())
        Assert.assertEquals(client.handleStarting(), NuguOAuthClient.AuthFlowState.REQUEST_ISSUE_TOKEN)
    }

    @Test
    fun testGetCredentials() {
        val client = NuguOAuthClient(mock())
        client.setCredentials(
                Credentials(
                    accessToken = "accessToken",
                    refreshToken = "refreshToken",
                    expiresIn = 10L,
                    issuedTime = Date().time,
                    tokenType = "",
                    scope = "device:S.I.D."
                )
        )
        Assert.assertNotNull(client.getCredentials())
        client.setCredentials(Credentials.getDefault())
        Assert.assertEquals(client.getCredentials(),Credentials.getDefault())
        //setRefreshToken
        client.setRefreshToken("refreshToken_updated")
        Assert.assertEquals(client.getCredentials().refreshToken,"refreshToken_updated")
    }

    @Test
    fun testIsExpired() {
        val client = NuguOAuthClient(mock())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 1000L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertFalse(client.isExpired())

        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = -1L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(client.isExpired())
    }

    @Test
    fun testBuildAuthorization() {
        val client = NuguOAuthClient(mock())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken1",
                refreshToken = "refreshToken",
                expiresIn = 1000L,
                issuedTime = Date().time,
                tokenType = "Bearer",
                scope = "device:S.I.D."
            )
        )
        client.setOptions(opts = NuguOAuthOptions.Builder()
            .deviceUniqueId("device2")
            .build())
        Assert.assertEquals(client.buildAuthorization(), "Bearer accessToken1")

        client.setCredentials(
            Credentials(
                accessToken = "accessToken2",
                refreshToken = "refreshToken",
                expiresIn = 1000L,
                issuedTime = Date().time,
                tokenType = "Bearer",
                scope = "device:S.I.D."
            )
        )
        Assert.assertEquals(client.buildAuthorization(), "Bearer accessToken2")
    }

    @Test
    fun testShouldRetry() {
        val client = NuguOAuthClient(mock())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 1000L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        client.setOptions(opts = NuguOAuthOptions.Builder()
            .deviceUniqueId("device2")
            .build())
        var attempt = 0
        Assert.assertTrue( "attempt=$attempt",
            client.shouldRetry(
                retriesAttempted = ++attempt,
                statusCode = 400,
                maxDelay = 300L
            )
        )
        Assert.assertTrue( "attempt=$attempt",
            client.shouldRetry(
                retriesAttempted = ++attempt,
                statusCode = 400,
                maxDelay = 300L
            )
        )
        Assert.assertTrue( "attempt=$attempt",
            client.shouldRetry(
                retriesAttempted = ++attempt,
                statusCode = 400,
                maxDelay = 300L
            )
        )
        Assert.assertTrue( "attempt=$attempt",
            client.shouldRetry(
                retriesAttempted = ++attempt,
                statusCode = 400,
                maxDelay = 300L
            )
        )
        Assert.assertFalse( "attempt=$attempt",
            client.shouldRetry(
                retriesAttempted = ++attempt,
                statusCode = 400,
                maxDelay = 300L
            )
        )
    }
}
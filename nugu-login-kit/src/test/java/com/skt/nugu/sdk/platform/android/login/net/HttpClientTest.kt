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
package com.skt.nugu.sdk.platform.android.login.net

import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthClient
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.ByteArrayInputStream
import java.net.MalformedURLException

class HttpClientTest {
    @Test
    fun testGetConnection() {
        val delegate = object : NuguOAuthClient.UrlDelegate {
            override fun baseUrl(): String {
                return "https://localhost"
            }
            override fun tokenEndpoint() = "endpoint"
            override fun authorizationEndpoint() = "endpoint"
            override fun introspectionEndpoint() = "endpoint"
            override fun revocationEndpoint() = "endpoint"
            override fun deviceAuthorizationEndpoint() = "endpoint"
            override fun meEndpoint() = "endpoint"
        }
        val client = HttpClient(delegate)
        val header = Headers()
            .add("name", "value")
        val connection = client.getConnection(
            uri = "${delegate.baseUrl()}/v1/auth/oauth/device_authorization",
            method = "GET",header)
        Assert.assertNotNull(connection)
    }
    @Test(expected = MalformedURLException::class)
    fun testGetConnectionUnknownProtocol() {
        val delegate = object : NuguOAuthClient.UrlDelegate {
            override fun baseUrl(): String {
                return "test://localhost"
            }
            override fun tokenEndpoint() = "endpoint"
            override fun authorizationEndpoint() = "endpoint"
            override fun introspectionEndpoint() = "endpoint"
            override fun revocationEndpoint() = "endpoint"
            override fun deviceAuthorizationEndpoint() = "endpoint"
            override fun meEndpoint() = "endpoint"
        }
        val client = HttpClient(delegate)
        val header = Headers()
            .add("name", "value")
        val connection = client.getConnection(
            uri = "${delegate.baseUrl()}/v1/auth/oauth/device_authorization",
            method = "GET",header)
        Assert.assertNotNull(connection)
    }

    @Test
    fun testReadStream() {
        val delegate = object : NuguOAuthClient.UrlDelegate {
            override fun baseUrl(): String {
                return "https://localhost"
            }
            override fun tokenEndpoint() = "endpoint"
            override fun authorizationEndpoint() = "endpoint"
            override fun introspectionEndpoint() = "endpoint"
            override fun revocationEndpoint() = "endpoint"
            override fun deviceAuthorizationEndpoint() = "endpoint"
            override fun meEndpoint() = "endpoint"
        }
        val client = HttpClient(delegate)
        val content = "Hello !!"
        val contentArray = content.toByteArray()
        val inputStream = ByteArrayInputStream(contentArray)
        val result = client.readStream(inputStream.buffered())
        Assert.assertEquals(result, content)
    }
}
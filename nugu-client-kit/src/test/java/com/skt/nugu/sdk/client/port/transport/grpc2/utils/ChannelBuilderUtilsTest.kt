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
package com.skt.nugu.sdk.client.port.transport.grpc2.utils

import com.skt.nugu.sdk.client.port.transport.grpc2.HeaderClientInterceptor
import com.skt.nugu.sdk.client.port.transport.grpc2.ServerPolicy
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.transport.ChannelOptions
import com.skt.nugu.sdk.core.interfaces.transport.IdleTimeout
import com.skt.nugu.sdk.core.utils.UserAgent

import junit.framework.TestCase
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.concurrent.TimeUnit

class ChannelBuilderUtilsTest : TestCase() {
    private val defaultServerPolicy = ServerPolicy("https", "localhost", 443, 1, 1000, "Normal")
    private val defaultAuthDelegate: AuthDelegate = mock()
    private val delegate: HeaderClientInterceptor.Delegate = mock()

    @Test
    fun testCreateChannelBuilderWith() {
        val channel = ChannelBuilderUtils.Companion
        channel.createChannelBuilderWith(
            defaultServerPolicy,
            ChannelOptions(IdleTimeout(10,TimeUnit.SECONDS)),
            defaultAuthDelegate,
            delegate
        ) { false }.build()
        assertNotNull(channel)
    }

    @Test
    fun testCreateChannelBuilderWithIsTerminated() {
        val channel = ChannelBuilderUtils.createChannelBuilderWith(
            defaultServerPolicy,
            ChannelOptions(IdleTimeout(10,TimeUnit.SECONDS)),
            defaultAuthDelegate,
            delegate
        ) { false }.build()
        assertFalse(channel.isTerminated)
    }

    @Test
    fun testCreateChannelBuilderWithShutdown() {
        val channel = ChannelBuilderUtils.createChannelBuilderWith(
            defaultServerPolicy,
            ChannelOptions(IdleTimeout(10,TimeUnit.SECONDS)),
            defaultAuthDelegate,
            delegate
        ) { false }.build()
        ChannelBuilderUtils.shutdown(channel)
        assertTrue(channel.isShutdown)
    }

    @Test
    fun testUserAgent() {
        UserAgent.setVersion("1.0.0", "2.0.0")
        assertTrue(ChannelBuilderUtils.userAgent().contains("1.0.0"))
        assertTrue(ChannelBuilderUtils.userAgent().contains("2.0.0"))
    }
}
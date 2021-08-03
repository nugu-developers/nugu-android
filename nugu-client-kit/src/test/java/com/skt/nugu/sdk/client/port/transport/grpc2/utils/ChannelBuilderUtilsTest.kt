package com.skt.nugu.sdk.client.port.transport.grpc2.utils

import com.skt.nugu.sdk.client.port.transport.grpc2.HeaderClientInterceptor
import com.skt.nugu.sdk.client.port.transport.grpc2.ServerPolicy
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.utils.UserAgent

import junit.framework.TestCase
import org.junit.Test
import org.mockito.kotlin.mock

class ChannelBuilderUtilsTest : TestCase() {
    val defaultServerPolicy = ServerPolicy("https", "localhost", 443,1,1000,"")
    val defaultAuthDelegate : AuthDelegate = mock()
    val delegate : HeaderClientInterceptor.Delegate = mock()
    @Test
    fun testCreateChannelBuilderWith() {
        val channel = ChannelBuilderUtils.createChannelBuilderWith(defaultServerPolicy, defaultAuthDelegate, delegate).build()
        assertFalse(channel.isTerminated)
    }
    @Test
    fun testShutdown() {
        val channel = ChannelBuilderUtils.createChannelBuilderWith(defaultServerPolicy, defaultAuthDelegate, delegate).build()
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
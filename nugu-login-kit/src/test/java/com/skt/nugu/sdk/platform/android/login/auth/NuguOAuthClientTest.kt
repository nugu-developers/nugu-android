package com.skt.nugu.sdk.platform.android.login.auth

import org.junit.Assert
import org.junit.Test

class NuguOAuthClientTest {
    @Test
    fun testUrlDelegate() {
        val delegate = object : NuguOAuthClient.UrlDelegate {
            override fun baseUrl(): String {
                return "baseUrl"
            }
        }
        Assert.assertEquals(delegate.baseUrl(),  "baseUrl")
    }
}
package com.skt.nugu.sdk.platform.android.login.net

import org.junit.Assert
import org.junit.Test

class RequestTest {
    @Test
    fun testRequests(){
        val header = Headers()
            .add("name", "value")
        val request = Request.Builder(
            uri = "/v1/auth/oauth/me",
            headers = header,
            method = "GET"
        ).build()
        Assert.assertEquals(request.method, "GET")
        Assert.assertEquals(request.uri, "/v1/auth/oauth/me")

        Assert.assertEquals(header.size(), request.headers?.size())
        Assert.assertEquals(header, request.headers)
    }
}
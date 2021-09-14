package com.skt.nugu.sdk.platform.android.login.net

import org.junit.Assert
import org.junit.Test

class ResponseTest {
    @Test
    fun testResponse() {
        val response = Response(200, "ok")
        Assert.assertEquals(response.code, 200)
        Assert.assertEquals(response.body, "ok")
    }
}
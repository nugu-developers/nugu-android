package com.skt.nugu.sdk.platform.android.login.auth

import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class IntrospectResponseTest {
    @Test
    fun `Parse JSON with IntrospectResponse`() {
        val response1 = IntrospectResponse(
            active = true,
            username = "dummy_username"
        )
        Assert.assertNotNull(response1)
        val response2 = IntrospectResponse.parse(
            "{\n" +
                     "  \"active\": true,\n" +
                    "  \"username\": \"dummy_username\"\n" +
                    "}"
        )
        Assert.assertNotNull(response2)
        Assert.assertEquals(response1,response2)
    }

    @Test(expected = JSONException::class)
    fun `JSONObject(active) not found`() {
        IntrospectResponse.parse(
            "{\n" +
                    // "  \"active\": true,\n" +
                    "  \"username\": \"dummy_username\"\n" +
                    "}"
        )
    }
}
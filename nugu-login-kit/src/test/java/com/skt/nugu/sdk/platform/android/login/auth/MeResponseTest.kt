package com.skt.nugu.sdk.platform.android.login.auth

import org.json.JSONException
import org.junit.Assert
import org.junit.Test
import java.util.*

class MeResponseTest {
    @Test
    fun `Parse JSON with MeResponse`() {
        val response1 = MeResponse(
            anonymous = true,
            deviceId = "dummy_deviceId",
            tid = "dummy_tid",
            userId = "dummy_userId"
        )
        Assert.assertNotNull(response1)
        val response2 = MeResponse.parse(
            "{\n" +
                     "  \"anonymous\": true,\n" +
                    "  \"deviceId\": \"dummy_deviceId\",\n" +
                    "  \"tid\": \"dummy_tid\",\n" +
                    "  \"userId\": \"dummy_userId\"\n" +
                    "}"
        )
        Assert.assertNotNull(response2)
        Assert.assertEquals(response1,response2)
    }

    @Test(expected = JSONException::class)
    fun `JSONObject(anonymous) not found`() {
        MeResponse.parse(
            "{\n" +
                   // "  \"anonymous\": true,\n" +
                    "  \"deviceId\": \"dummy_deviceId\",\n" +
                    "  \"tid\": \"dummy_tid\",\n" +
                    "  \"userId\": \"dummy_userId\"\n" +
                    "}"
        )
    }
}